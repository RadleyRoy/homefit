package com.radley.homefit.platform

import com.radley.homefit.data.db.ReminderEntity
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class ReminderTimesTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    // Wednesday 11 Nov 2026, 18:00.
    private val now = ZonedDateTime.of(2026, 11, 11, 18, 0, 0, 0, zone)

    private fun reminder(hour: Int, minute: Int, vararg days: DayOfWeek) =
        ReminderEntity(id = 1, hour = hour, minute = minute, daysMask = ReminderTimes.mask(days.toSet()), enabled = true)

    @Test
    fun `later today when today is selected`() {
        val next = ReminderTimes.next(reminder(19, 30, *DayOfWeek.entries.toTypedArray()), now)
        assertEquals(ZonedDateTime.of(2026, 11, 11, 19, 30, 0, 0, zone), next)
    }

    @Test
    fun `tomorrow when today's time has passed`() {
        val next = ReminderTimes.next(reminder(7, 0, *DayOfWeek.entries.toTypedArray()), now)
        assertEquals(ZonedDateTime.of(2026, 11, 12, 7, 0, 0, 0, zone), next)
    }

    @Test
    fun `skips to the next selected weekday`() {
        val next = ReminderTimes.next(reminder(9, 0, DayOfWeek.MONDAY), now)
        assertEquals(ZonedDateTime.of(2026, 11, 16, 9, 0, 0, 0, zone), next)
    }

    @Test
    fun `same weekday next week when this week's slot has passed`() {
        val next = ReminderTimes.next(reminder(17, 0, DayOfWeek.WEDNESDAY), now)
        assertEquals(ZonedDateTime.of(2026, 11, 18, 17, 0, 0, 0, zone), next)
    }

    @Test
    fun `exactly now is not "after now"`() {
        val next = ReminderTimes.next(reminder(18, 0, DayOfWeek.WEDNESDAY), now)
        assertEquals(ZonedDateTime.of(2026, 11, 18, 18, 0, 0, 0, zone), next)
    }

    @Test
    fun `no days means never`() {
        assertNull(ReminderTimes.next(ReminderEntity(id = 1, hour = 9, minute = 0, daysMask = 0, enabled = true), now))
    }

    @Test
    fun `mask round-trips`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)
        assertEquals(days, ReminderTimes.days(ReminderTimes.mask(days)))
    }
}
