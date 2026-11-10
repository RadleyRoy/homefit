package com.radley.homefit.coach

import com.radley.homefit.data.content.Measure
import com.radley.homefit.data.content.WorkoutItem
import com.radley.homefit.testing.bundledContent
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class CoachImportTest {

    private val content = bundledContent

    @Test
    fun `reads a tagged block out of a whole reply`() {
        val reply = """
            This mixes legs and core, which you haven't trained this week.

            ```homefit
            workout: Core and legs
            rest: 20
            jumping_jacks 30s
            squats x15
            plank 40s
            ```

            Enjoy!
        """.trimIndent()
        val workout = CoachImport.parse(reply, content).single()
        assertEquals("Core and legs", workout.name)
        assertEquals(20, workout.restSeconds)
        assertEquals(
            listOf(
                WorkoutItem("jumping_jacks", Measure.Timed(30)),
                WorkoutItem("squats", Measure.Reps(15)),
                WorkoutItem("plank", Measure.Timed(40)),
            ),
            workout.items,
        )
        assertTrue(workout.skipped.isEmpty())
    }

    @Test
    fun `accepts display names, bullets and wordier measures`() {
        val text = """
            1. Push-Ups - 12 reps
            - Wall Sit: 1 min
            * cobra stretch 30 sec
            • Jumping Jacks ×20
        """.trimIndent()
        val items = CoachImport.parse(text, content).single().items
        assertEquals(
            listOf(
                WorkoutItem("push_ups", Measure.Reps(12)),
                WorkoutItem("wall_sit", Measure.Timed(60)),
                WorkoutItem("cobra_stretch", Measure.Timed(30)),
                WorkoutItem("jumping_jacks", Measure.Reps(20)),
            ),
            items,
        )
    }

    @Test
    fun `a week reply becomes one workout per block`() {
        val reply = """
            ```homefit
            workout: Monday: Legs
            squats x15
            ```
            ```homefit
            workout: Wednesday: Upper body
            push_ups x10
            ```
        """.trimIndent()
        assertEquals(listOf("Monday: Legs", "Wednesday: Upper body"), CoachImport.parse(reply, content).map { it.name })
    }

    @Test
    fun `unknown moves are reported, not guessed`() {
        val reply = """
            ```homefit
            workout: Mixed
            squats x15
            kettlebell swings x20
            ```
        """.trimIndent()
        val workout = CoachImport.parse(reply, content).single()
        assertEquals(listOf(WorkoutItem("squats", Measure.Reps(15))), workout.items)
        assertEquals(listOf("kettlebell swings x20"), workout.skipped)
    }

    @Test
    fun `out-of-range numbers are clamped`() {
        val items = CoachImport.parse("squats x5000\nplank 1s", content).single().items
        assertEquals(listOf(WorkoutItem("squats", Measure.Reps(200)), WorkoutItem("plank", Measure.Timed(5))), items)
    }

    @Test
    fun `prose with no moves imports nothing`() {
        assertTrue(CoachImport.parse("Rest today and drink some water.", content).isEmpty())
    }

    @Test
    fun `an untitled block gets a default name`() {
        assertEquals("Claude workout", CoachImport.parse("squats x10", content).single().name)
    }
}
