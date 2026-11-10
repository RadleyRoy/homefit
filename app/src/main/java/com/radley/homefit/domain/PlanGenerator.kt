package com.radley.homefit.domain

import com.radley.homefit.data.Goal
import com.radley.homefit.data.Profile
import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.Equipment
import com.radley.homefit.data.content.Exercise
import com.radley.homefit.data.content.ExerciseCategory
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.Measure
import com.radley.homefit.data.content.WorkoutItem
import java.time.LocalDate
import kotlin.random.Random

sealed interface PlanDayResult {
    data object Rest : PlanDayResult

    data class Train(
        val title: String,
        val focus: FocusArea,
        val weekNumber: Int,
        val items: List<WorkoutItem>,
    ) : PlanDayResult
}

/**
 * The offline "coach": turns a [Profile] into a weekly schedule of generated sessions.
 *
 * Every day is a pure function of (profile, plan start, seed, date), so nothing about the plan
 * is stored beyond the start date and seed. Revisiting a past day shows exactly what was
 * scheduled, and editing the profile reshapes the plan from that point on.
 *
 * Each session is warm-up → main block → cool-down. The main block draws from the day's focus
 * area, mixed with cardio according to the goal, and grows ~5% a week for the first two months.
 */
class PlanGenerator(private val content: Content) {

    fun dayFor(profile: Profile, startEpochDay: Long, seed: Long, epochDay: Long, restSeconds: Int): PlanDayResult {
        val date = LocalDate.ofEpochDay(epochDay)
        val trainingDays = profile.trainingDays.sorted()
        if (trainingDays.isEmpty() || date.dayOfWeek !in profile.trainingDays) return PlanDayResult.Rest

        val week = ((epochDay - startEpochDay) / 7).coerceAtLeast(0).toInt()
        val sessionIndex = week * trainingDays.size + trainingDays.indexOf(date.dayOfWeek)
        val rotation = FocusArea.entries.filter { it in profile.focusAreas }.ifEmpty { listOf(FocusArea.FULL_BODY) }
        val focus = rotation[sessionIndex % rotation.size]
        val random = Random(seed * 31 + sessionIndex)

        val eligible = content.exercises.values.filter { eligible(it, profile) }
        val volume = levelFactor(profile.level) * (1 + PROGRESSION_PER_WEEK * week.coerceAtMost(PROGRESSION_WEEKS))

        val warmUp = pick(eligible.filter { it.id in WARM_UP_IDS }, 2, random)
            .map { WorkoutItem(it.id, Measure.Timed(if (profile.level == Level.BEGINNER) 20 else 30)) }

        val mainCount = mainBlockSize(profile.minutesPerSession, restSeconds)
        val main = mainBlock(eligible, focus, profile.goal, mainCount, random).map { exercise ->
            val goalBoost = if (profile.goal == Goal.BUILD_MUSCLE && exercise.defaultMeasure is Measure.Reps) 1.1 else 1.0
            WorkoutItem(exercise.id, exercise.defaultMeasure.scaled(volume * goalBoost))
        }

        val coolDown = pick(
            eligible.filter { it.category == ExerciseCategory.STRETCH && it.id !in WARM_UP_IDS && it.matches(focus) }
                .ifEmpty { eligible.filter { it.category == ExerciseCategory.STRETCH } },
            2,
            random,
        ).map { WorkoutItem(it.id, Measure.Timed(30)) }

        return PlanDayResult.Train(
            title = "${focus.label} · Week ${week + 1}",
            focus = focus,
            weekNumber = week + 1,
            items = warmUp + main + coolDown,
        )
    }

    private fun mainBlock(pool: List<Exercise>, focus: FocusArea, goal: Goal, count: Int, random: Random): List<Exercise> {
        val strength = pool.filter {
            it.category == ExerciseCategory.STRENGTH && it.id !in WARM_UP_IDS && it.matches(focus)
        }
        val cardio = pool.filter {
            (it.category == ExerciseCategory.CARDIO || it.category == ExerciseCategory.PLYOMETRIC) && it.id !in WARM_UP_IDS
        }
        // One cardio move after every N strength moves.
        val cardioEvery = when (goal) {
            Goal.LOSE_WEIGHT -> 2
            Goal.KEEP_FIT -> 3
            Goal.BUILD_MUSCLE -> Int.MAX_VALUE
        }
        val strengthQueue = ArrayDeque(cycle(strength.shuffled(random), count))
        val cardioQueue = ArrayDeque(cycle(cardio.shuffled(random), count))
        val out = mutableListOf<Exercise>()
        var sinceCardio = 0
        while (out.size < count) {
            val takeCardio = sinceCardio >= cardioEvery && cardioQueue.isNotEmpty()
            val next = if (takeCardio || strengthQueue.isEmpty()) cardioQueue.removeFirstOrNull() else strengthQueue.removeFirst()
            next ?: break
            out += next
            sinceCardio = if (takeCardio) 0 else sinceCardio + 1
        }
        return out
    }

    private fun eligible(e: Exercise, profile: Profile): Boolean =
        e.equipment in HOME_EQUIPMENT &&
            e.level.ordinal <= profile.level.ordinal &&
            (!profile.quietMode || e.noJump)

    private fun Exercise.matches(focus: FocusArea) = focus == FocusArea.FULL_BODY || focus in this.focus

    private fun <T> pick(pool: List<T>, n: Int, random: Random): List<T> = pool.shuffled(random).take(n)

    /** Repeats the list to [n] items, so a narrow pool (advanced + quiet mode) still fills a session. */
    private fun <T> cycle(list: List<T>, n: Int): List<T> =
        if (list.isEmpty()) emptyList() else List(n) { list[it % list.size] }

    companion object {
        private val HOME_EQUIPMENT = setOf(Equipment.NONE, Equipment.WALL, Equipment.CHAIR)
        private val WARM_UP_IDS = setOf(
            "jumping_jacks", "march_in_place", "arm_circles", "high_knees", "butt_kicks", "hip_circles", "shoulder_circles",
        )
        private const val PROGRESSION_PER_WEEK = 0.05
        private const val PROGRESSION_WEEKS = 8
        private const val WARM_UP_AND_COOL_DOWN_SECONDS = 150
        private const val SECONDS_PER_MOVE = 40

        fun levelFactor(level: Level) = when (level) {
            Level.BEGINNER -> 0.8
            Level.INTERMEDIATE -> 1.0
            Level.ADVANCED -> 1.3
        }

        fun mainBlockSize(minutes: Int, restSeconds: Int): Int =
            ((minutes * 60 - WARM_UP_AND_COOL_DOWN_SECONDS) / (SECONDS_PER_MOVE + restSeconds)).coerceIn(4, 14)
    }
}
