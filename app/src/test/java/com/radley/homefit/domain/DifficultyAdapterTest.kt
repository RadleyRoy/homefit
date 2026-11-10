package com.radley.homefit.domain

import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.Measure
import com.radley.homefit.data.content.WorkoutItem
import com.radley.homefit.testing.exercise
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class DifficultyAdapterTest {

    private val content = Content(
        exercises = listOf(
            exercise("knee_push_ups", harder = "push_ups"),
            exercise("push_ups", easier = "knee_push_ups", harder = "decline"),
            exercise("decline", easier = "push_ups"),
            exercise("plank", measure = Measure.Timed(30)),
            exercise("knee_plank", measure = Measure.Timed(20)),
        ).associateBy { it.id },
        workouts = emptyMap(),
        programs = emptyMap(),
    )
    private val items = listOf(WorkoutItem("push_ups", Measure.Reps(12)), WorkoutItem("plank", Measure.Timed(30)))

    @Test
    fun `one too-hard eases the multiplier without swapping`() {
        val out = DifficultyAdapter.apply(DifficultyState(), Feedback.TOO_HARD, items, content)
        assertEquals(0.9, out.state.multiplier, 1e-9)
        assertEquals(1, out.state.hardStreak)
        assertNull(out.swap)
    }

    @Test
    fun `two too-hards in a row swap to easier variants`() {
        val first = DifficultyAdapter.apply(DifficultyState(), Feedback.TOO_HARD, items, content).state
        val second = DifficultyAdapter.apply(first, Feedback.TOO_HARD, items, content)
        assertEquals(SwapDirection.EASIER, second.swap)
        assertEquals(mapOf("push_ups" to "knee_push_ups"), second.state.swaps)
        assertEquals(0, second.state.hardStreak)
    }

    @Test
    fun `just right resets streaks but keeps the multiplier`() {
        val out = DifficultyAdapter.apply(DifficultyState(1.2, 1, 0), Feedback.JUST_RIGHT, items, content)
        assertEquals(DifficultyState(1.2, 0, 0), out.state)
    }

    @Test
    fun `multiplier is clamped`() {
        var state = DifficultyState()
        repeat(20) { state = DifficultyAdapter.apply(state, Feedback.TOO_EASY, items, content).state }
        assertEquals(DifficultyAdapter.MAX, state.multiplier)
    }

    @Test
    fun `swap chains collapse`() {
        val there = DifficultyAdapter.merge(emptyMap(), mapOf("push_ups" to "decline"))
        assertEquals(mapOf("push_ups" to "decline"), there)
        val back = DifficultyAdapter.merge(there, mapOf("decline" to "push_ups"))
        assertEquals(emptyMap(), back)
        val further = DifficultyAdapter.merge(mapOf("knee_push_ups" to "push_ups"), mapOf("push_ups" to "decline"))
        assertEquals(mapOf("knee_push_ups" to "decline"), further)
    }

    @Test
    fun `substitution keeps the measure when the kind matches`() {
        val out = DifficultyAdapter.substitute(items, mapOf("push_ups" to "decline", "plank" to "knee_plank"), content)
        assertEquals(
            listOf(WorkoutItem("decline", Measure.Reps(12)), WorkoutItem("knee_plank", Measure.Timed(30))),
            out,
        )
    }

    @Test
    fun `swap encoding round-trips`() {
        val swaps = mapOf("a" to "b", "c" to "d")
        assertEquals(swaps, DifficultyAdapter.decodeSwaps(DifficultyAdapter.encodeSwaps(swaps)))
        assertEquals(emptyMap(), DifficultyAdapter.decodeSwaps(""))
    }
}
