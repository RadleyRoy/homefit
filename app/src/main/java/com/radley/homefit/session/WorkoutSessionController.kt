package com.radley.homefit.session

import android.content.Context
import android.os.SystemClock
import com.radley.homefit.data.HistoryRepository
import com.radley.homefit.data.ProgramRepository
import com.radley.homefit.data.WorkoutPrefs
import com.radley.homefit.data.WorkoutRepository
import com.radley.homefit.data.db.SessionEntity
import com.radley.homefit.domain.Achievement
import com.radley.homefit.domain.Calories
import com.radley.homefit.domain.Feedback
import com.radley.homefit.domain.ResolvedWorkout
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.domain.player.Cue
import com.radley.homefit.domain.player.Phase
import com.radley.homefit.domain.player.PlayerEngine
import com.radley.homefit.domain.player.PlayerSnapshot
import com.radley.homefit.domain.player.spoken
import com.radley.homefit.platform.VoiceCoach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ActiveWorkout(
    val workout: ResolvedWorkout,
    val snapshot: PlayerSnapshot,
    val startedAt: Long,
)

sealed interface SessionEvent {
    data class Finished(val sessionId: Long) : SessionEvent
    data object Ended : SessionEvent
}

/**
 * Runs the workout in process scope rather than in a ViewModel, so the timer and the voice
 * coach carry on when the screen turns off or the user glances at another app. The player
 * screen and [WorkoutService]'s notification are both just views of [active].
 */
class WorkoutSessionController(
    private val context: Context,
    private val voice: () -> VoiceCoach,
    private val history: HistoryRepository,
    private val programs: ProgramRepository,
    private val workouts: WorkoutRepository,
    /** Health Connect and anything else that wants to hear about a saved session. */
    private val onRecorded: suspend (SessionEntity) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _active = MutableStateFlow<ActiveWorkout?>(null)
    val active: StateFlow<ActiveWorkout?> = _active.asStateFlow()

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    /** Badges earned by the most recent session, for the completion screen. */
    var lastEarned: List<Achievement> = emptyList()
        private set

    /** True from the last exercise ending until the session is in the database. */
    @Volatile
    var isSaving: Boolean = false
        private set

    private var engine: PlayerEngine? = null
    private var workout: ResolvedWorkout? = null
    private var prefs = WorkoutPrefs()
    private var startedAt = 0L
    private var ticker: Job? = null
    private var finishing = false

    fun start(workout: ResolvedWorkout, prefs: WorkoutPrefs) {
        if (workout.steps.isEmpty()) return
        ticker?.cancel()
        this.workout = workout
        this.prefs = prefs
        startedAt = System.currentTimeMillis()
        finishing = false
        isSaving = false
        engine = PlayerEngine(workout.steps, prefs.countdownSeconds * 1000L, workout.restSeconds * 1000L)
        changed()
        ticker = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                engine?.advance(now - last)
                last = now
                changed()
            }
        }
        WorkoutService.start(context)
    }

    fun togglePause() = act { if (it.snapshot.paused) it.resume() else it.pause() }
    fun pause() = act { it.pause() }
    fun resume() = act { it.resume() }
    fun skip() = act { it.skip() }
    fun previous() = act { it.previous() }
    fun complete() = act { it.complete() }
    fun extendRest() = act { it.extendRest(EXTEND_REST_MS) }

    /**
     * Leaves the workout. With [save], whatever was completed is recorded as a session (the
     * "finish now" option); otherwise nothing is kept. A "too hard" reason still counts as
     * feedback, since struggling enough to quit is exactly what adaptive difficulty is for.
     */
    fun quit(save: Boolean, reason: Feedback? = null) {
        val w = workout ?: return
        if (reason != null) scope.launch { workouts.applyFeedback(w.ref, reason) }
        if (save && (engine?.snapshot?.completed?.isNotEmpty() == true)) {
            finish()
        } else {
            end()
            _events.tryEmit(SessionEvent.Ended)
        }
    }

    private inline fun act(block: (PlayerEngine) -> Unit) {
        engine?.let(block)
        changed()
    }

    private fun changed() {
        val e = engine ?: return
        val w = workout ?: return
        speak(e.drainCues())
        _active.value = ActiveWorkout(w, e.snapshot, startedAt)
        if (e.snapshot.phase == Phase.Finished) finish()
    }

    private fun finish() {
        if (finishing) return
        finishing = true
        val e = engine ?: return
        val w = workout ?: return
        val snapshot = e.snapshot
        ticker?.cancel()
        isSaving = true
        scope.launch {
            val weight = history.latestWeightKg()
            val session = SessionEntity(
                startedAt = startedAt,
                endedAt = System.currentTimeMillis(),
                activeSeconds = (snapshot.activeMs / 1000).toInt(),
                totalSeconds = (snapshot.elapsedMs / 1000).toInt(),
                kcal = w.steps.indices.sumOf { Calories.kcal(w.steps[it].exercise.met, weight, snapshot.activeMsPerStep[it]) },
                refKey = w.ref.encode(),
                title = listOfNotNull(w.subtitle?.takeIf { w.ref is WorkoutRef.ProgramDay }, w.title).joinToString(" · "),
                category = w.category.name,
                focus = w.focus?.name,
                exercisesCompleted = snapshot.completed.size,
                exercisesTotal = w.steps.size,
            )
            val id = history.record(session)
            (w.ref as? WorkoutRef.ProgramDay)?.let { ref ->
                workouts.content().programs[ref.programId]?.let { programs.completeDay(it, ref.day) }
            }
            lastEarned = history.evaluateAchievements()
            runCatching { onRecorded(session.copy(id = id)) }
            // Let "workout complete" finish playing; only quitting silences the coach.
            end(silence = false)
            // isSaving stays set until the next start(): the player may observe `active` going
            // null before it receives this event, and must not mistake that for an abandon.
            _events.emit(SessionEvent.Finished(id))
        }
    }

    private fun end(silence: Boolean = true) {
        ticker?.cancel()
        ticker = null
        engine = null
        workout = null
        _active.value = null
        if (silence) voice().stop()
        WorkoutService.stop(context)
    }

    private fun speak(cues: List<Cue>) {
        if (cues.isEmpty()) return
        val coach = voice()
        for (cue in cues) {
            if (prefs.soundEffects) {
                when (cue) {
                    is Cue.Countdown -> coach.beep()
                    is Cue.Start -> coach.beep(long = true)
                    else -> Unit
                }
            }
            if (!prefs.voiceEnabled) continue
            when (cue) {
                is Cue.GetReady ->
                    coach.say("Ready to go. First up, ${cue.step.exercise.name}, ${cue.step.measure.spoken(cue.step.exercise.perSide)}.")
                is Cue.Countdown -> coach.say(cue.number.toString(), interrupt = true)
                is Cue.Start -> coach.say(
                    if (cue.announce) "${cue.step.exercise.name}, ${cue.step.measure.spoken(cue.step.exercise.perSide)}." else "Go!",
                    interrupt = true,
                )
                Cue.Halfway -> coach.say("Halfway there, keep going.")
                Cue.SwitchSides -> coach.say("Switch sides.", interrupt = true)
                is Cue.RestStarted -> {
                    coach.say(
                        "Take a rest. ${cue.seconds} seconds. Next, ${cue.next.exercise.name}, " +
                            "${cue.next.measure.spoken(cue.next.exercise.perSide)}.",
                    )
                    if (prefs.coachTips) cue.next.exercise.tips.firstOrNull()?.let { coach.say(it) }
                }
                Cue.Finished -> coach.say("Well done! Workout complete.")
            }
        }
    }

    companion object {
        private const val TICK_MS = 100L
        const val EXTEND_REST_MS = 20_000L
    }
}
