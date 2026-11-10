package com.radley.homefit.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radley.homefit.data.AppPrefs
import com.radley.homefit.data.Profile
import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.db.SessionEntity
import com.radley.homefit.data.db.WeightEntity
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.Achievements
import com.radley.homefit.domain.BodyMetrics
import com.radley.homefit.domain.Streaks
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReportState(
    val loading: Boolean = true,
    val totalWorkouts: Int = 0,
    val totalKcal: Double = 0.0,
    val totalMinutes: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val weekDates: List<LocalDate> = emptyList(),
    val weekMinutes: List<Float> = emptyList(),
    val todayIndex: Int = 0,
    val month: YearMonth = YearMonth.now(),
    val workoutDays: Set<Long> = emptySet(),
    val firstDayOfWeek: java.time.DayOfWeek = java.time.DayOfWeek.MONDAY,
    val recent: List<SessionEntity> = emptyList(),
    val weights: List<WeightEntity> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
    val heightCm: Double? = null,
    val targetWeightKg: Double? = null,
    val bmi: Double? = null,
    val badgesEarned: Int = 0,
    val badgesTotal: Int = Achievements.all.size,
)

class ReportViewModel : ViewModel() {

    private val history = ServiceLocator.history
    private val settings = ServiceLocator.settings
    private val month = MutableStateFlow(YearMonth.now())

    private data class Inputs(val sessions: List<SessionEntity>, val weights: List<WeightEntity>, val profile: Profile, val app: AppPrefs)

    private val inputs = combine(history.sessions, history.weights, settings.profile, settings.appPrefs, ::Inputs)

    val state: StateFlow<ReportState> = combine(inputs, month, history.unlockedAchievements) { inputs, month, badges ->
        val (sessions, weights, profile, app) = inputs
        val today = history.today()
        val days = sessions.map { history.localDay(it.startedAt) }.toSet()
        val week = Streaks.weekDates(today, app.weekStart.dayOfWeek)
        val minutesByDay = sessions.groupBy { history.localDay(it.startedAt) }.mapValues { (_, s) -> s.sumOf { it.totalSeconds } / 60f }
        val latest = weights.lastOrNull()?.kg
        ReportState(
            loading = false,
            totalWorkouts = sessions.size,
            totalKcal = sessions.sumOf { it.kcal },
            totalMinutes = sessions.sumOf { it.totalSeconds } / 60,
            currentStreak = Streaks.current(days, today.toEpochDay()),
            bestStreak = Streaks.best(days),
            weekDates = week,
            weekMinutes = week.map { minutesByDay[it.toEpochDay()] ?: 0f },
            todayIndex = week.indexOf(today),
            month = month,
            workoutDays = days,
            firstDayOfWeek = app.weekStart.dayOfWeek,
            recent = sessions.take(5),
            weights = weights,
            units = app.units,
            heightCm = profile.heightCm,
            targetWeightKg = profile.targetWeightKg,
            bmi = if (latest != null && profile.heightCm != null) BodyMetrics.bmi(latest, profile.heightCm) else null,
            badgesEarned = badges.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportState())

    fun shiftMonth(by: Long) {
        month.value = month.value.plusMonths(by)
    }

    fun logWeight(date: LocalDate, value: Double, units: UnitSystem) = viewModelScope.launch {
        history.logWeight(date.toEpochDay(), if (units == UnitSystem.METRIC) value else BodyMetrics.lbToKg(value))
        history.evaluateAchievements()
    }

    fun deleteWeight(epochDay: Long) = viewModelScope.launch { history.deleteWeight(epochDay) }

    fun setHeight(cm: Double) = viewModelScope.launch { settings.updateProfile { it.copy(heightCm = cm) } }
}
