package com.radley.homefit.data

import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import java.time.DayOfWeek

enum class Sex(val label: String) { FEMALE("Female"), MALE("Male"), UNSPECIFIED("Prefer not to say") }

enum class Goal(val label: String, val blurb: String) {
    LOSE_WEIGHT("Lose weight", "More cardio intervals and calorie burn"),
    BUILD_MUSCLE("Build muscle", "More strength work and higher reps"),
    KEEP_FIT("Keep fit", "A balanced mix of strength, cardio and mobility"),
}

enum class UnitSystem { METRIC, IMPERIAL }

enum class WeekStart(val dayOfWeek: DayOfWeek, val label: String) {
    MONDAY(DayOfWeek.MONDAY, "Monday"),
    SUNDAY(DayOfWeek.SUNDAY, "Sunday"),
    SATURDAY(DayOfWeek.SATURDAY, "Saturday"),
}

/** What the plan generator and calorie estimates know about the user. */
data class Profile(
    val sex: Sex = Sex.UNSPECIFIED,
    val birthYear: Int? = null,
    val heightCm: Double? = null,
    val goal: Goal = Goal.KEEP_FIT,
    val level: Level = Level.BEGINNER,
    val focusAreas: Set<FocusArea> = setOf(FocusArea.FULL_BODY),
    val weeklyGoal: Int = 3,
    val trainingDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    val minutesPerSession: Int = 15,
    val targetWeightKg: Double? = null,
    /** Leave out jumping moves: thin floors, sore knees, or a sleeping baby. */
    val quietMode: Boolean = false,
)

data class WorkoutPrefs(
    val restSeconds: Int = 30,
    val countdownSeconds: Int = 15,
    val voiceEnabled: Boolean = true,
    val coachTips: Boolean = true,
    val soundEffects: Boolean = true,
    val keepScreenOn: Boolean = true,
) {
    companion object {
        val REST_RANGE = 5..180
        val COUNTDOWN_RANGE = 5..30
    }
}

data class AppPrefs(
    val units: UnitSystem = UnitSystem.METRIC,
    val weekStart: WeekStart = WeekStart.MONDAY,
    val onboardingComplete: Boolean = false,
    /** First day of the personalised plan; week numbers are counted from here. */
    val planStartEpochDay: Long? = null,
    /** Fixed per plan so regenerating a day always gives the same exercises. */
    val planSeed: Long = 0,
    val healthConnectSync: Boolean = false,
)
