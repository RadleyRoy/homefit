package com.radley.homefit.domain.player

import com.radley.homefit.data.content.Measure
import com.radley.homefit.domain.ResolvedStep

sealed interface Phase {
    /** The "get ready" countdown before the first exercise. */
    data class Ready(val index: Int, val remainingMs: Long, val totalMs: Long) : Phase

    /** Doing exercise [index]. Timed moves end on their own; rep counts wait for [PlayerEngine.complete]. */
    data class Work(val index: Int, val elapsedMs: Long) : Phase

    data class Rest(val nextIndex: Int, val remainingMs: Long, val totalMs: Long) : Phase

    data object Finished : Phase
}

/** What the voice coach and sound effects react to. Drained by the caller after each call. */
sealed interface Cue {
    data class GetReady(val step: ResolvedStep) : Cue
    data class Countdown(val number: Int) : Cue

    /**
     * [announce] is set when no rest preceded this step (back-to-back intervals), so the coach
     * names the move here instead of during a rest that never happened.
     */
    data class Start(val step: ResolvedStep, val announce: Boolean = false) : Cue
    data object Halfway : Cue
    data object SwitchSides : Cue
    data class RestStarted(val seconds: Int, val next: ResolvedStep) : Cue
    data object Finished : Cue
}

data class PlayerSnapshot(
    val phase: Phase,
    val paused: Boolean,
    /** Steps finished by time running out or by tapping Done; skipped ones aren't counted. */
    val completed: Set<Int>,
    /** Time spent in exercises, per step; drives the calorie estimate. */
    val activeMsPerStep: List<Long>,
    /** Wall time excluding pauses. */
    val elapsedMs: Long,
) {
    val activeMs: Long get() = activeMsPerStep.sum()
}

/**
 * The workout state machine, with no Android or coroutine dependencies. The caller drives it
 * with [advance] and measured time deltas (from `SystemClock.elapsedRealtime`), which keeps it
 * deterministic in tests and immune to timer drift: a stalled frame or a stretch in the
 * background just arrives as a larger delta, and [advance] walks through every phase boundary
 * it covers instead of losing them.
 */
class PlayerEngine(
    val steps: List<ResolvedStep>,
    private val readyMs: Long,
    private val restMs: Long,
) {
    init {
        require(steps.isNotEmpty()) { "A workout needs at least one exercise" }
    }

    private var phase: Phase = Phase.Ready(0, readyMs, readyMs)
    private var paused = false
    private val completed = mutableSetOf<Int>()
    private val activeMs = LongArray(steps.size)
    private var elapsedMs = 0L
    private val pendingCues = mutableListOf<Cue>(Cue.GetReady(steps[0]))

    val snapshot: PlayerSnapshot
        get() = PlayerSnapshot(phase, paused, completed.toSet(), activeMs.toList(), elapsedMs)

    fun drainCues(): List<Cue> = pendingCues.toList().also { pendingCues.clear() }

    fun advance(deltaMs: Long) {
        var remaining = deltaMs
        while (remaining > 0 && !paused && phase != Phase.Finished) {
            remaining -= consume(remaining)
        }
    }

    fun pause() {
        if (phase != Phase.Finished) paused = true
    }

    fun resume() {
        paused = false
    }

    /** "Done" on a rep-based exercise; also ends a timed one early but still counts it. */
    fun complete() {
        val work = phase as? Phase.Work ?: return
        completed += work.index
        afterWork(work.index)
    }

    /** Next: leaves the current exercise uncounted, or cuts a rest/get-ready short. */
    fun skip() {
        when (val p = phase) {
            is Phase.Ready -> startWork(p.index)
            is Phase.Work -> afterWork(p.index)
            is Phase.Rest -> startWork(p.nextIndex)
            Phase.Finished -> Unit
        }
    }

    /** Previous: restarts the exercise before this one (or the first, from the first). */
    fun previous() {
        when (val p = phase) {
            is Phase.Ready -> Unit
            is Phase.Work -> startWork((p.index - 1).coerceAtLeast(0))
            is Phase.Rest -> startWork((p.nextIndex - 1).coerceAtLeast(0))
            Phase.Finished -> Unit
        }
    }

    fun extendRest(extraMs: Long) {
        val p = phase as? Phase.Rest ?: return
        phase = p.copy(remainingMs = p.remainingMs + extraMs, totalMs = p.totalMs + extraMs)
    }

    /** Returns how much of [delta] the current phase used up. */
    private fun consume(delta: Long): Long = when (val p = phase) {
        is Phase.Ready -> {
            val used = minOf(delta, p.remainingMs)
            countdown(p.remainingMs, p.remainingMs - used)
            elapsedMs += used
            if (used == p.remainingMs) startWork(p.index) else phase = p.copy(remainingMs = p.remainingMs - used)
            used
        }
        is Phase.Rest -> {
            val used = minOf(delta, p.remainingMs)
            countdown(p.remainingMs, p.remainingMs - used)
            elapsedMs += used
            if (used == p.remainingMs) startWork(p.nextIndex) else phase = p.copy(remainingMs = p.remainingMs - used)
            used
        }
        is Phase.Work -> {
            val step = steps[p.index]
            val target = step.targetMs
            val used = if (target == null) delta else minOf(delta, target - p.elapsedMs)
            val after = p.elapsedMs + used
            if (target != null) {
                countdown(target - p.elapsedMs, target - after)
                if (crossed(p.elapsedMs, after, target / 2)) {
                    when {
                        step.exercise.perSide -> pendingCues += Cue.SwitchSides
                        target >= HALFWAY_MIN_MS -> pendingCues += Cue.Halfway
                    }
                }
            }
            activeMs[p.index] += used
            elapsedMs += used
            if (target != null && after >= target) {
                completed += p.index
                afterWork(p.index)
            } else {
                phase = p.copy(elapsedMs = after)
            }
            used
        }
        Phase.Finished -> delta
    }

    private fun startWork(index: Int, announce: Boolean = false) {
        phase = Phase.Work(index, 0)
        pendingCues += Cue.Start(steps[index], announce)
    }

    private fun afterWork(index: Int) {
        when {
            index == steps.lastIndex -> {
                phase = Phase.Finished
                paused = false
                pendingCues += Cue.Finished
            }
            // Interval sessions (run/walk) flow straight from one step into the next.
            restMs == 0L -> startWork(index + 1, announce = true)
            else -> {
                phase = Phase.Rest(index + 1, restMs, restMs)
                pendingCues += Cue.RestStarted((restMs / 1000).toInt(), steps[index + 1])
            }
        }
    }

    /** "3, 2, 1" as the remaining time passes each whole second, for countdowns long enough to need it. */
    private fun countdown(before: Long, after: Long) {
        for (n in 3 downTo 1) {
            if (before > n * 1000L && after <= n * 1000L) pendingCues += Cue.Countdown(n)
        }
    }

    private fun crossed(before: Long, after: Long, mark: Long) = before < mark && after >= mark

    companion object {
        /** Holds shorter than this get no "halfway" call; it would land on top of the countdown. */
        const val HALFWAY_MIN_MS = 20_000L
    }
}

/** Seconds for display: rounds up, so a timer never shows 0 while time is left. */
fun Long.ceilSeconds(): Int = ((this + 999) / 1000).toInt()

fun Measure.spoken(perSide: Boolean): String {
    val side = if (perSide) " each side" else ""
    return when (this) {
        is Measure.Reps -> "$count reps$side"
        is Measure.Timed -> "$seconds seconds$side"
    }
}
