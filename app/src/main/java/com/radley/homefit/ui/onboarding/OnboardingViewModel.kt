package com.radley.homefit.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radley.homefit.data.Goal
import com.radley.homefit.data.Profile
import com.radley.homefit.data.Sex
import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.db.ReminderEntity
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.BodyMetrics
import com.radley.homefit.domain.PlanDayResult
import com.radley.homefit.domain.PlanGenerator
import com.radley.homefit.platform.ReminderTimes
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { WELCOME, GOAL, FOCUS, LEVEL, BODY, SCHEDULE, REMINDER, PREVIEW }

data class PreviewDay(val date: LocalDate, val title: String?, val moves: Int)

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val profile: Profile = Profile(),
    val units: UnitSystem = UnitSystem.METRIC,
    /** Entered in the display unit; converted on save. */
    val weightText: String = "",
    val heightText: String = "",
    val reminderOn: Boolean = true,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val preview: List<PreviewDay> = emptyList(),
    /** True when opened from Me → My plan rather than first launch. */
    val editing: Boolean = false,
)

class OnboardingViewModel(editing: Boolean) : ViewModel() {

    private val settings = ServiceLocator.settings

    /** Chosen once per view model, so the preview and the saved plan match. */
    private val seed = Random.nextLong()

    private val _state = MutableStateFlow(OnboardingState(step = if (editing) OnboardingStep.GOAL else OnboardingStep.WELCOME, editing = editing))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = settings.currentProfile()
            val units = settings.currentAppPrefs().units
            val weight = if (ServiceLocator.history.hasWeight()) ServiceLocator.history.latestWeightKg() else null
            _state.update {
                it.copy(
                    profile = profile,
                    units = units,
                    weightText = weight?.let { kg -> "%.1f".format(if (units == UnitSystem.METRIC) kg else BodyMetrics.kgToLb(kg)) } ?: "",
                    heightText = profile.heightCm?.let { cm -> "%.0f".format(if (units == UnitSystem.METRIC) cm else BodyMetrics.cmToInches(cm)) } ?: "",
                    reminderOn = !editing,
                )
            }
        }
    }

    fun profile(transform: (Profile) -> Profile) = _state.update { it.copy(profile = transform(it.profile)) }
    fun setUnits(units: UnitSystem) = _state.update { it.copy(units = units, weightText = "", heightText = "") }
    fun setWeight(text: String) = _state.update { it.copy(weightText = text) }
    fun setHeight(text: String) = _state.update { it.copy(heightText = text) }
    fun setReminder(on: Boolean, hour: Int = _state.value.reminderHour, minute: Int = _state.value.reminderMinute) =
        _state.update { it.copy(reminderOn = on, reminderHour = hour, reminderMinute = minute) }

    fun toggleFocus(area: FocusArea) = profile { p ->
        val next = if (area in p.focusAreas) p.focusAreas - area else p.focusAreas + area
        p.copy(focusAreas = next.ifEmpty { p.focusAreas })
    }

    fun toggleDay(day: DayOfWeek) = profile { p ->
        val next = if (day in p.trainingDays) p.trainingDays - day else p.trainingDays + day
        if (next.isEmpty()) p else p.copy(trainingDays = next, weeklyGoal = next.size)
    }

    fun next() {
        val steps = OnboardingStep.entries
        val s = _state.value
        val nextStep = steps.getOrNull(s.step.ordinal + 1)
            // Editing an existing plan doesn't need the reminder step; reminders have their own screen.
            ?.let { if (s.editing && it == OnboardingStep.REMINDER) OnboardingStep.PREVIEW else it }
            ?: return
        if (nextStep == OnboardingStep.PREVIEW) buildPreview()
        _state.update { it.copy(step = nextStep) }
    }

    fun back(): Boolean {
        val s = _state.value
        val first = if (s.editing) OnboardingStep.GOAL else OnboardingStep.WELCOME
        if (s.step == first) return false
        val prev = OnboardingStep.entries[s.step.ordinal - 1]
            .let { if (s.editing && it == OnboardingStep.REMINDER) OnboardingStep.SCHEDULE else it }
        _state.update { it.copy(step = prev) }
        return true
    }

    private fun buildPreview() = viewModelScope.launch {
        val content = ServiceLocator.content.get()
        val today = LocalDate.now()
        val rest = settings.currentWorkoutPrefs().restSeconds
        val generator = PlanGenerator(content)
        val profile = _state.value.profile
        val days = (0L until 7L).map { today.plusDays(it) }.map { date ->
            when (val d = generator.dayFor(profile, today.toEpochDay(), seed, date.toEpochDay(), rest)) {
                is PlanDayResult.Train -> PreviewDay(date, d.title, d.items.size)
                PlanDayResult.Rest -> PreviewDay(date, null, 0)
            }
        }
        _state.update { it.copy(preview = days) }
    }

    /** Skip on the welcome screen: everything but the plan works without a profile. */
    fun skip(onDone: () -> Unit) = viewModelScope.launch {
        settings.updateAppPrefs { it.copy(onboardingComplete = true) }
        onDone()
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        val metric = s.units == UnitSystem.METRIC
        val height = s.heightText.replace(',', '.').toDoubleOrNull()?.let { if (metric) it else BodyMetrics.inchesToCm(it) }
        val weight = s.weightText.replace(',', '.').toDoubleOrNull()?.let { if (metric) it else BodyMetrics.lbToKg(it) }

        settings.setProfile(s.profile.copy(heightCm = height ?: s.profile.heightCm))
        weight?.let { ServiceLocator.history.logWeight(LocalDate.now().toEpochDay(), it) }
        val existing = settings.currentAppPrefs()
        settings.updateAppPrefs {
            it.copy(
                units = s.units,
                onboardingComplete = true,
                // Changing the plan restarts it at week one; the preview the user just saw is
                // exactly what they get, because it was built with the same start and seed.
                planStartEpochDay = LocalDate.now().toEpochDay(),
                planSeed = seed,
            )
        }
        if (s.reminderOn && !s.editing) {
            val reminders = ServiceLocator.reminders
            if (reminders.reminders.first().isEmpty()) {
                reminders.save(ReminderEntity(hour = s.reminderHour, minute = s.reminderMinute, daysMask = ReminderTimes.mask(s.profile.trainingDays), enabled = true))
            }
        }
        if (existing.planStartEpochDay == null) ServiceLocator.history.evaluateAchievements()
        onDone()
    }
}

val Level.pushUpRange: String
    get() = when (this) {
        Level.BEGINNER -> "0–5 push-ups"
        Level.INTERMEDIATE -> "6–15 push-ups"
        Level.ADVANCED -> "16 or more"
    }
