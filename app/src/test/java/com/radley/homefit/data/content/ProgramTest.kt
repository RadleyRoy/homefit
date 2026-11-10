package com.radley.homefit.data.content

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.Test

class ProgramTest {

    private val program = Program(
        id = "p",
        name = "P",
        description = "",
        category = ProgramCategory.CHALLENGE,
        focus = FocusArea.FULL_BODY,
        days = 28,
        restDays = setOf(4, 8),
        progression = Progression.Weekly(listOf(1.0, 1.2, 1.4, 1.6)),
        levels = mapOf(Level.BEGINNER to listOf("a", "b")),
    )

    private fun template(day: Int) = (program.dayPlan(day, Level.BEGINNER) as ProgramDay.Train).templateId

    @Test
    fun `rest days don't advance the A-B rotation`() {
        assertEquals(listOf("a", "b", "a"), (1..3).map(::template))
        assertIs<ProgramDay.Rest>(program.dayPlan(4, Level.BEGINNER))
        assertEquals("b", template(5))
        assertEquals("a", template(6))
    }

    @Test
    fun `weekly progression steps up every seven days`() {
        assertEquals(1.0, (program.dayPlan(7, Level.BEGINNER) as ProgramDay.Train).factor)
        assertEquals(1.2, (program.dayPlan(9, Level.BEGINNER) as ProgramDay.Train).factor)
        assertEquals(1.6, (program.dayPlan(28, Level.BEGINNER) as ProgramDay.Train).factor)
    }

    @Test
    fun `a missing level falls back to the first available`() {
        assertEquals("a", (program.dayPlan(1, Level.ADVANCED) as ProgramDay.Train).templateId)
    }

    @Test
    fun `linear progression runs from first to last day`() {
        val linear = Progression.Linear(1.0, 3.0)
        assertEquals(1.0, linear.factorFor(1, 30))
        assertEquals(3.0, linear.factorFor(30, 30))
    }

    @Test
    fun `measures scale and never collapse to zero`() {
        assertEquals(Measure.Reps(15), Measure.Reps(10).scaled(1.5))
        assertEquals(Measure.Timed(45), Measure.Timed(30).scaled(1.5))
        assertEquals(Measure.Reps(1), Measure.Reps(1).scaled(0.1))
        assertEquals(Measure.Timed(5), Measure.Timed(10).scaled(0.1))
    }
}
