package com.radley.homefit.domain

import com.radley.homefit.data.Goal
import com.radley.homefit.data.Profile
import com.radley.homefit.data.content.Equipment
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.testing.bundledContent
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class PlanGeneratorTest {

    private val generator = PlanGenerator(bundledContent)
    private val monday = LocalDate.of(2026, 11, 9).toEpochDay()
    private val profile = Profile(
        goal = Goal.LOSE_WEIGHT,
        level = Level.INTERMEDIATE,
        focusAreas = setOf(FocusArea.ABS, FocusArea.LEGS),
        trainingDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        minutesPerSession = 20,
    )

    private fun day(offset: Long, p: Profile = profile) =
        generator.dayFor(p, monday, seed = 42, epochDay = monday + offset, restSeconds = 30)

    @Test
    fun `days outside the schedule are rest days`() {
        assertIs<PlanDayResult.Rest>(day(1)) // Tuesday
        assertIs<PlanDayResult.Train>(day(2)) // Wednesday
    }

    @Test
    fun `focus rotates through the chosen areas`() {
        val focuses = listOf(0L, 2, 4, 7).map { (day(it) as PlanDayResult.Train).focus }
        assertEquals(listOf(FocusArea.ABS, FocusArea.LEGS, FocusArea.ABS, FocusArea.LEGS), focuses)
    }

    @Test
    fun `the same day always generates the same session`() {
        assertEquals(day(0), day(0))
    }

    @Test
    fun `sessions use only home equipment and respect quiet mode`() {
        val quiet = profile.copy(quietMode = true, level = Level.ADVANCED)
        for (offset in 0L until 28) {
            val train = day(offset, quiet) as? PlanDayResult.Train ?: continue
            for (item in train.items) {
                val e = bundledContent.exercise(item.exerciseId)
                assertTrue(e.noJump, "${e.id} jumps in quiet mode")
                assertTrue(e.equipment in setOf(Equipment.NONE, Equipment.WALL, Equipment.CHAIR), "${e.id} needs ${e.equipment}")
            }
        }
    }

    @Test
    fun `beginners never get harder moves`() {
        val beginner = profile.copy(level = Level.BEGINNER)
        for (offset in 0L until 28) {
            val train = day(offset, beginner) as? PlanDayResult.Train ?: continue
            assertTrue(train.items.all { bundledContent.exercise(it.exerciseId).level == Level.BEGINNER })
        }
    }

    @Test
    fun `losing weight mixes in cardio`() {
        val train = day(0) as PlanDayResult.Train
        val cardio = train.items.count {
            bundledContent.exercise(it.exerciseId).category.name in setOf("CARDIO", "PLYOMETRIC")
        }
        assertTrue(cardio >= 3, "expected cardio intervals, got $cardio")
    }

    @Test
    fun `session length tracks the requested minutes`() {
        assertEquals(4, PlanGenerator.mainBlockSize(5, 30))
        assertEquals(14, PlanGenerator.mainBlockSize(60, 30))
        assertTrue(PlanGenerator.mainBlockSize(20, 30) in 10..14)
    }

    @Test
    fun `week numbers count from the plan start`() {
        assertEquals(1, (day(0) as PlanDayResult.Train).weekNumber)
        assertEquals(5, (day(28) as PlanDayResult.Train).weekNumber)
    }
}
