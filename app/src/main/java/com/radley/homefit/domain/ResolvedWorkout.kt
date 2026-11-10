package com.radley.homefit.domain

import com.radley.homefit.data.content.Exercise
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.Measure
import com.radley.homefit.data.content.WorkoutCategory
import com.radley.homefit.data.content.WorkoutItem

data class ResolvedStep(val exercise: Exercise, val measure: Measure) {

    /** Both sides for per-side moves; null for rep counts, which end when the user says so. */
    val targetMs: Long?
        get() = (measure as? Measure.Timed)?.let { it.seconds * 1000L * sides }

    val sides: Int get() = if (exercise.perSide) 2 else 1

    /** A rough figure for previews; rep-based moves are assumed to take about 3 s a rep. */
    val estimatedSeconds: Int
        get() = when (val m = measure) {
            is Measure.Timed -> m.seconds * sides
            is Measure.Reps -> m.count * SECONDS_PER_REP * sides
        }

    companion object {
        const val SECONDS_PER_REP = 3
    }
}

/** Everything the detail screen and the player need, with overrides and difficulty applied. */
data class ResolvedWorkout(
    val ref: WorkoutRef,
    val title: String,
    val subtitle: String?,
    val description: String,
    val category: WorkoutCategory,
    val focus: FocusArea?,
    val level: Level?,
    val restSeconds: Int,
    val steps: List<ResolvedStep>,
    /** What the editor starts from: overrides applied, adaptive difficulty not. */
    val editableItems: List<WorkoutItem>,
    val isEdited: Boolean,
    val difficultyMultiplier: Double,
    /** False for coach proposals, which are edited by saving them as a custom workout first. */
    val canEdit: Boolean,
) {
    val estimatedSeconds: Int
        get() = steps.sumOf { it.estimatedSeconds } + restSeconds * (steps.size - 1).coerceAtLeast(0)

    fun estimatedKcal(weightKg: Double): Double =
        steps.sumOf { Calories.kcal(it.exercise.met, weightKg, it.estimatedSeconds * 1000L) }
}
