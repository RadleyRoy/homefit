package com.radley.homefit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radley.homefit.data.AppPrefs
import com.radley.homefit.data.Profile
import com.radley.homefit.data.ProgramStatus
import com.radley.homefit.data.WorkoutPrefs
import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.ProgramCategory
import com.radley.homefit.data.db.SessionEntity
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.PlanDayResult
import com.radley.homefit.domain.PlanGenerator
import com.radley.homefit.domain.Streaks
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.WorkoutCardModel
import com.radley.homefit.ui.components.estimate
import com.radley.homefit.ui.components.toCard
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayCell(val date: LocalDate, val done: Boolean, val isToday: Boolean, val planned: Boolean)

sealed interface TodayCard {
    data class Train(val card: WorkoutCardModel, val doneToday: Boolean) : TodayCard
    data class Rest(val nextTraining: LocalDate?) : TodayCard
    data object NoPlan : TodayCard
}

data class HomeUiState(
    val loading: Boolean = true,
    val week: List<DayCell> = emptyList(),
    val weekDone: Int = 0,
    val weeklyGoal: Int = 3,
    val streak: Int = 0,
    val today: TodayCard = TodayCard.NoPlan,
    val challenges: List<ProgramStatus> = emptyList(),
    val focusByLevel: Map<Level, List<WorkoutCardModel>> = emptyMap(),
    val recent: List<SessionEntity> = emptyList(),
)

class HomeViewModel : ViewModel() {

    private val settings = ServiceLocator.settings
    private val history = ServiceLocator.history
    private val programs = ServiceLocator.programs
    private val content = flow { emit(ServiceLocator.content.get()) }

    private data class Inputs(
        val content: Content,
        val profile: Profile,
        val app: AppPrefs,
        val prefs: WorkoutPrefs,
        val sessions: List<SessionEntity>,
    )

    private val inputs = combine(content, settings.profile, settings.appPrefs, settings.workoutPrefs, history.sessions) { c, p, a, w, s ->
        Inputs(c, p, a, w, s)
    }

    val state: StateFlow<HomeUiState> = combine(inputs, programs.progress, history.weights) { inputs, progress, weights ->
        val (content, profile, app, prefs, sessions) = inputs
        val weight = weights.lastOrNull()?.kg ?: com.radley.homefit.domain.Calories.DEFAULT_WEIGHT_KG
        val today = history.today()
        val days = sessions.map { history.localDay(it.startedAt) }.toSet()
        val week = Streaks.weekDates(today, app.weekStart.dayOfWeek).map {
            DayCell(it, it.toEpochDay() in days, it == today, it.dayOfWeek in profile.trainingDays)
        }

        HomeUiState(
            loading = false,
            week = week,
            weekDone = week.count { it.done },
            weeklyGoal = profile.weeklyGoal,
            streak = Streaks.current(days, today.toEpochDay()),
            today = todayCard(content, profile, app, prefs, weight, today, days),
            challenges = content.programs.values
                .filter { it.category == ProgramCategory.CHALLENGE }
                .map { programs.status(it, progress[it.id]) }
                .sortedByDescending { it.started && !it.finished },
            focusByLevel = Level.entries.associateWith { level ->
                FocusArea.entries.mapNotNull { focus -> content.focusWorkout(focus, level)?.toCard(content, prefs.restSeconds, weight) }
            },
            recent = sessions.take(3),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun todayCard(
        content: Content,
        profile: Profile,
        app: AppPrefs,
        prefs: WorkoutPrefs,
        weight: Double,
        today: LocalDate,
        workoutDays: Set<Long>,
    ): TodayCard {
        val start = app.planStartEpochDay ?: return TodayCard.NoPlan
        val generator = PlanGenerator(content)
        return when (val day = generator.dayFor(profile, start, app.planSeed, today.toEpochDay(), prefs.restSeconds)) {
            is PlanDayResult.Train -> {
                val (seconds, kcal) = estimate(day.items, content, prefs.restSeconds, weight)
                TodayCard.Train(
                    WorkoutCardModel(
                        ref = WorkoutRef.PlanDay(today.toEpochDay()),
                        title = day.title,
                        subtitle = "Today's plan",
                        category = com.radley.homefit.data.content.WorkoutCategory.GENERATED,
                        focus = day.focus,
                        level = profile.level,
                        seconds = seconds,
                        exerciseCount = day.items.size,
                        kcal = kcal,
                    ),
                    doneToday = today.toEpochDay() in workoutDays,
                )
            }
            PlanDayResult.Rest -> TodayCard.Rest(
                (1L..7L).map { today.plusDays(it) }.firstOrNull { it.dayOfWeek in profile.trainingDays },
            )
        }
    }

    fun setWeeklyGoal(goal: Int) = viewModelScope.launch {
        settings.updateProfile { it.copy(weeklyGoal = goal.coerceIn(1, 7)) }
    }
}
