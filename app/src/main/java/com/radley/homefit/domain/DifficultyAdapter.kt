package com.radley.homefit.domain

import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.Measure
import com.radley.homefit.data.content.WorkoutItem

enum class Feedback(val label: String) {
    TOO_EASY("Too easy"),
    JUST_RIGHT("Just right"),
    TOO_HARD("Too hard"),
}

data class DifficultyState(
    val multiplier: Double = 1.0,
    val hardStreak: Int = 0,
    val easyStreak: Int = 0,
    /** Original exercise id → the variant that replaces it. */
    val swaps: Map<String, String> = emptyMap(),
)

enum class SwapDirection { EASIER, HARDER }

data class DifficultyOutcome(val state: DifficultyState, val swap: SwapDirection?)

/**
 * Turns post-workout feedback into a reps/time multiplier, the way a coach would: one "too
 * hard" eases off a little; a second in a row means the moves themselves are wrong, so they get
 * swapped for their easier variants (and the reverse for "too easy").
 */
object DifficultyAdapter {

    const val STEP = 0.10
    const val MIN = 0.6
    const val MAX = 1.6
    const val SWAP_AFTER = 2

    /**
     * [items] are the workout's moves as last played (swaps already applied); they're needed to
     * know which variants a swap would pick.
     */
    fun apply(state: DifficultyState, feedback: Feedback, items: List<WorkoutItem>, content: Content): DifficultyOutcome =
        when (feedback) {
            Feedback.JUST_RIGHT -> DifficultyOutcome(state.copy(hardStreak = 0, easyStreak = 0), null)
            Feedback.TOO_HARD -> {
                val streak = state.hardStreak + 1
                val swap = streak >= SWAP_AFTER
                DifficultyOutcome(
                    state.copy(
                        multiplier = (state.multiplier * (1 - STEP)).coerceAtLeast(MIN),
                        hardStreak = if (swap) 0 else streak,
                        easyStreak = 0,
                        swaps = if (swap) merge(state.swaps, variants(items, content, SwapDirection.EASIER)) else state.swaps,
                    ),
                    if (swap) SwapDirection.EASIER else null,
                )
            }
            Feedback.TOO_EASY -> {
                val streak = state.easyStreak + 1
                val swap = streak >= SWAP_AFTER
                DifficultyOutcome(
                    state.copy(
                        multiplier = (state.multiplier * (1 + STEP)).coerceAtMost(MAX),
                        hardStreak = 0,
                        easyStreak = if (swap) 0 else streak,
                        swaps = if (swap) merge(state.swaps, variants(items, content, SwapDirection.HARDER)) else state.swaps,
                    ),
                    if (swap) SwapDirection.HARDER else null,
                )
            }
        }

    /** current id → variant id, for each move that has a variant in [direction]. */
    fun variants(items: List<WorkoutItem>, content: Content, direction: SwapDirection): Map<String, String> =
        items.mapNotNull { item ->
            val exercise = content.exercises[item.exerciseId] ?: return@mapNotNull null
            val variant = if (direction == SwapDirection.EASIER) exercise.easierId else exercise.harderId
            variant?.takeIf { it in content.exercises }?.let { item.exerciseId to it }
        }.toMap()

    /**
     * Folds new substitutions into existing ones so chains collapse: if squats were already
     * swapped to jump squats and jump squats now go back to squats, the entry disappears rather
     * than becoming squats → squats.
     */
    fun merge(existing: Map<String, String>, new: Map<String, String>): Map<String, String> {
        val result = existing.mapValues { (_, to) -> new[to] ?: to }.toMutableMap()
        val alreadyTargets = existing.values.toSet()
        for ((from, to) in new) if (from !in alreadyTargets && from !in result) result[from] = to
        return result.filterNot { (from, to) -> from == to }
    }

    /**
     * Applies [swaps]. When the variant is measured differently (a timed hold swapped for a rep
     * exercise) it takes the variant's own default, since "30 seconds" of push-ups and "x12" of
     * a plank are both meaningless.
     */
    fun substitute(items: List<WorkoutItem>, swaps: Map<String, String>, content: Content): List<WorkoutItem> {
        if (swaps.isEmpty()) return items
        return items.map { item ->
            val variant = swaps[item.exerciseId]?.let { content.exercises[it] } ?: return@map item
            val sameKind = (item.measure is Measure.Reps) == (variant.defaultMeasure is Measure.Reps)
            WorkoutItem(variant.id, if (sameKind) item.measure else variant.defaultMeasure)
        }
    }

    fun encodeSwaps(swaps: Map<String, String>): String = swaps.entries.joinToString(";") { "${it.key}>${it.value}" }

    fun decodeSwaps(encoded: String): Map<String, String> =
        if (encoded.isBlank()) emptyMap()
        else encoded.split(';').associate { pair -> pair.split('>').let { it[0] to it[1] } }
}
