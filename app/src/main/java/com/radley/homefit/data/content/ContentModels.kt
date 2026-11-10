package com.radley.homefit.data.content

import kotlin.math.roundToInt

enum class FocusArea(val label: String) {
    ABS("Abs"),
    CHEST("Chest"),
    ARMS("Arms"),
    LEGS("Legs"),
    BUTT("Butt"),
    SHOULDERS_BACK("Shoulders & Back"),
    FULL_BODY("Full Body"),
}

enum class Level(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
}

enum class Equipment(val label: String) {
    NONE("No equipment"),
    CHAIR("Chair"),
    WALL("Wall"),
    DUMBBELL("Dumbbells"),
    BAR("Bar or table"),
    OUTDOORS("Outdoors or treadmill"),
}

enum class ExerciseCategory { STRENGTH, CARDIO, PLYOMETRIC, STRETCH }

enum class WorkoutCategory(val label: String, val inLibrary: Boolean) {
    FOCUS("Body focus", true),
    WARMUP("Warm-up", true),
    STRETCH("Stretch", true),
    HIIT("HIIT & fat burn", true),
    QUICK("Quick workouts", true),
    LOW_IMPACT("Low impact", true),
    CHAIR("Chair workouts", true),
    DUMBBELL("With dumbbells", true),
    RUNNING("Running", true),
    SPECIAL("Popular", true),

    /** Templates that only exist to be scheduled by a [Program]. */
    PROGRAM_DAY("Program day", false),
    CUSTOM("My workouts", false),

    /** Built on the fly by the plan generator or the LLM coach. */
    GENERATED("Generated", false),
}

enum class ProgramCategory { CHALLENGE, RECOVERY }

/**
 * How much of an exercise to do. Content files and the database both store it in the same
 * compact form — `x12` for reps, `30s` for a hold or interval — via [encode] and [parse].
 */
sealed interface Measure {
    data class Reps(val count: Int) : Measure
    data class Timed(val seconds: Int) : Measure

    fun encode(): String = when (this) {
        is Reps -> "x$count"
        is Timed -> "${seconds}s"
    }

    /** Never scales below one rep or five seconds, so a heavy downshift can't zero a move out. */
    fun scaled(factor: Double): Measure = when (this) {
        is Reps -> Reps((count * factor).roundToInt().coerceAtLeast(1))
        is Timed -> Timed(roundToFive(seconds * factor).coerceAtLeast(5))
    }

    companion object {
        fun parse(text: String): Measure {
            val t = text.trim()
            return when {
                t.startsWith("x") -> Reps(t.drop(1).toInt())
                t.endsWith("s") -> Timed(t.dropLast(1).toInt())
                else -> throw IllegalArgumentException("Unrecognised measure \"$text\"")
            }
        }

        /** Timers read better as :05 multiples than as 37 seconds. */
        private fun roundToFive(seconds: Double): Int = ((seconds / 5.0).roundToInt() * 5)
    }
}

data class Exercise(
    val id: String,
    val name: String,
    val category: ExerciseCategory,
    val level: Level,
    val equipment: Equipment,
    val focus: List<FocusArea>,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val defaultMeasure: Measure,
    val perSide: Boolean,
    val met: Double,
    val noJump: Boolean,
    val instructions: List<String>,
    val tips: List<String>,
    /** Paths relative to `assets/content/images`; empty for exercises without photos yet. */
    val images: List<String>,
    val easierId: String?,
    val harderId: String?,
)

data class WorkoutItem(val exerciseId: String, val measure: Measure)

data class WorkoutTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: WorkoutCategory,
    val focus: FocusArea?,
    val level: Level,
    /** Null means "use the rest length from settings". */
    val restSeconds: Int?,
    val tags: Set<String>,
    val items: List<WorkoutItem>,
)

sealed interface Progression {
    /** One multiplier per week; weeks past the end keep the last step. */
    data class Weekly(val steps: List<Double>) : Progression

    /** Straight ramp from [from] on day one to [to] on the last day. */
    data class Linear(val from: Double, val to: Double) : Progression

    fun factorFor(day: Int, totalDays: Int): Double = when (this) {
        is Weekly -> steps[((day - 1) / 7).coerceAtMost(steps.lastIndex)]
        is Linear -> if (totalDays <= 1) from else from + (to - from) * (day - 1) / (totalDays - 1)
    }
}

sealed interface ProgramDay {
    data object Rest : ProgramDay
    data class Train(val templateId: String, val factor: Double) : ProgramDay
}

data class Program(
    val id: String,
    val name: String,
    val description: String,
    val category: ProgramCategory,
    val focus: FocusArea,
    val days: Int,
    val restDays: Set<Int>,
    val progression: Progression,
    val levels: Map<Level, List<String>>,
) {
    val availableLevels: List<Level> get() = Level.entries.filter { it in levels }

    /**
     * Rest days don't advance the template rotation, so an A/B program alternates A, B, A, B
     * across the training days rather than skipping a letter after every rest.
     */
    fun dayPlan(day: Int, level: Level): ProgramDay {
        require(day in 1..days) { "Day $day outside 1..$days" }
        if (day in restDays) return ProgramDay.Rest
        val templates = levels[level] ?: levels.getValue(availableLevels.first())
        val trainingDaysBefore = (1 until day).count { it !in restDays }
        return ProgramDay.Train(
            templateId = templates[trainingDaysBefore % templates.size],
            factor = progression.factorFor(day, days),
        )
    }
}
