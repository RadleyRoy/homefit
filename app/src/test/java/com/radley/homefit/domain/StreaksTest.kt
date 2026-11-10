package com.radley.homefit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

class StreaksTest {

    private val today = LocalDate.of(2026, 11, 11).toEpochDay() // a Wednesday

    @Test
    fun `streak counts back from today`() {
        assertEquals(3, Streaks.current(setOf(today, today - 1, today - 2, today - 4), today))
    }

    @Test
    fun `a streak survives until a full day is missed`() {
        assertEquals(2, Streaks.current(setOf(today - 1, today - 2), today))
        assertEquals(0, Streaks.current(setOf(today - 2, today - 3), today))
    }

    @Test
    fun `best streak finds the longest run`() {
        assertEquals(4, Streaks.best(setOf(1, 2, 3, 4, 10, 11, 20)))
        assertEquals(0, Streaks.best(emptySet()))
    }

    @Test
    fun `week boundaries follow the chosen first day`() {
        val wed = LocalDate.of(2026, 11, 11)
        assertEquals(LocalDate.of(2026, 11, 9), Streaks.weekStart(wed, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 8), Streaks.weekStart(wed, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 7), Streaks.weekStart(wed, DayOfWeek.SATURDAY))
    }

    @Test
    fun `days this week ignores last week`() {
        val wed = LocalDate.of(2026, 11, 11)
        val days = setOf(wed.toEpochDay(), wed.minusDays(2).toEpochDay(), wed.minusDays(3).toEpochDay())
        assertEquals(2, Streaks.daysThisWeek(days, wed, DayOfWeek.MONDAY))
        assertEquals(3, Streaks.daysThisWeek(days, wed, DayOfWeek.SUNDAY))
    }
}
