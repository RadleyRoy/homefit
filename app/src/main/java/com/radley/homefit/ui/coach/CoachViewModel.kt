package com.radley.homefit.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radley.homefit.coach.CoachContext
import com.radley.homefit.coach.CoachImport
import com.radley.homefit.coach.CoachPrompts
import com.radley.homefit.coach.ImportedWorkout
import com.radley.homefit.coach.RecentSession
import com.radley.homefit.coach.WorkoutRequest
import com.radley.homefit.data.WorkoutRepository
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.Calories
import com.radley.homefit.domain.Feedback
import com.radley.homefit.domain.Streaks
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.WorkoutCardModel
import com.radley.homefit.ui.discover.toCard
import java.time.Instant
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CoachAsk {
    data class Question(val text: String) : CoachAsk
    data class Workout(val request: WorkoutRequest) : CoachAsk
    data object Week : CoachAsk
    data object Review : CoachAsk
}

/**
 * Builds prompts for the Claude app and reads its workouts back. Nothing here talks to a
 * network: the screen hands the prompt to Claude, and replies arrive by Share or Paste.
 */
class CoachViewModel : ViewModel() {

    private val settings = ServiceLocator.settings
    private val history = ServiceLocator.history

    /** Workouts Claude designed that the user imported, newest first. */
    val fromClaude: StateFlow<List<WorkoutCardModel>> = combine(
        flow { emit(ServiceLocator.content.get()) },
        ServiceLocator.workouts.customWorkouts,
        settings.workoutPrefs,
        history.weights,
    ) { content, custom, prefs, weights ->
        val kg = weights.lastOrNull()?.kg ?: Calories.DEFAULT_WEIGHT_KG
        custom.filter { it.origin == WorkoutRepository.ORIGIN_COACH }
            .sortedByDescending { it.createdAt }
            .map { it.toCard(content, prefs.restSeconds, kg) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Workouts parsed from a reply, waiting for the user to confirm the import. */
    private val _pending = MutableStateFlow<List<ImportedWorkout>?>(null)
    val pending: StateFlow<List<ImportedWorkout>?> = _pending.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _open = Channel<WorkoutRef>(Channel.BUFFERED)
    val open = _open.receiveAsFlow()

    init {
        // Share → HomeFit lands here, whether the screen was already open or not.
        viewModelScope.launch {
            ServiceLocator.coachInbox.filterNotNull().collect { text ->
                ServiceLocator.coachInbox.value = null
                import(text)
            }
        }
    }

    suspend fun prompt(ask: CoachAsk): String {
        val content = ServiceLocator.content.get()
        val ctx = context()
        return when (ask) {
            is CoachAsk.Question -> CoachPrompts.ask(ctx, ask.text)
            is CoachAsk.Workout -> CoachPrompts.workout(ctx, content, ask.request)
            CoachAsk.Week -> CoachPrompts.week(ctx, content)
            CoachAsk.Review -> CoachPrompts.review(ctx, content)
        }
    }

    fun import(text: String?) = viewModelScope.launch {
        if (text.isNullOrBlank()) {
            _messages.send("The clipboard is empty. In Claude, tap Copy under the reply first.")
            return@launch
        }
        val workouts = CoachImport.parse(text, ServiceLocator.content.get())
        if (workouts.isEmpty()) {
            _messages.send("No workout found in that text. Ask Claude for a workout from the cards above, then copy its whole reply.")
        } else {
            _pending.value = workouts
        }
    }

    fun confirmImport() = viewModelScope.launch {
        val workouts = _pending.value ?: return@launch
        _pending.value = null
        val ids = workouts.map { w ->
            ServiceLocator.workouts.createCustom(w.name, w.items, restSeconds = w.restSeconds, origin = WorkoutRepository.ORIGIN_COACH)
        }
        if (ids.size == 1) {
            _open.send(WorkoutRef.Custom(ids.single()))
        } else {
            _messages.send("Saved ${ids.size} workouts from Claude. They're listed below and under Discover → My workouts.")
        }
    }

    fun dismissImport() {
        _pending.value = null
    }

    private suspend fun context(): CoachContext {
        val profile = settings.currentProfile()
        val app = settings.currentAppPrefs()
        val today = history.today()
        val zone = java.time.ZoneId.systemDefault()
        val sessions = history.sessions.first()
        val days = history.workoutDays.first()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(app.weekStart.dayOfWeek)).toEpochDay()
        val since = today.minusDays(RECENT_DAYS).toEpochDay()
        return CoachContext(
            profile = profile,
            units = app.units,
            today = today,
            latestWeightKg = if (history.hasWeight()) history.latestWeightKg() else null,
            restSeconds = settings.currentWorkoutPrefs().restSeconds,
            workoutsThisWeek = days.count { it in weekStart..today.toEpochDay() },
            streakDays = Streaks.current(days, today.toEpochDay()),
            recent = sessions
                .map { s -> s to Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate() }
                .filter { (_, date) -> date.toEpochDay() >= since }
                .sortedByDescending { (s, _) -> s.startedAt }
                .map { (s, date) ->
                    RecentSession(date, s.title, s.totalSeconds / 60, s.feedback?.let { f -> runCatching { Feedback.valueOf(f) }.getOrNull() })
                },
        )
    }

    private companion object {
        const val RECENT_DAYS = 28L
    }
}
