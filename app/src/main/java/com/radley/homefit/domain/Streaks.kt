package com.radley.homefit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Streaks and weekly goal, computed from the set of days that had at least one workout. */
object Streaks {

    /**
     * Consecutive workout days ending today. A streak isn't broken until a whole day passes
     * without a workout, so if today has none yet the count runs back from yesterday.
     */
    fun current(workoutDays: Set<Long>, today: Long): Int {
        val anchor = when {
            today in workoutDays -> today
            today - 1 in workoutDays -> today - 1
            else -> return 0
        }
        var day = anchor
        var count = 0
        while (day in workoutDays) {
            count++
            day--
        }
        return count
    }

    fun best(workoutDays: Set<Long>): Int {
        var best = 0
        for (day in workoutDays) {
            if (day - 1 in workoutDays) continue // only count from the start of each run
            var length = 0
            while (day + length in workoutDays) length++
            best = maxOf(best, length)
        }
        return best
    }

    fun weekStart(date: LocalDate, firstDay: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    /** The seven dates of the week containing [date], in display order. */
    fun weekDates(date: LocalDate, firstDay: DayOfWeek): List<LocalDate> {
        val start = weekStart(date, firstDay)
        return (0L until 7L).map { start.plusDays(it) }
    }

    fun daysThisWeek(workoutDays: Set<Long>, today: LocalDate, firstDay: DayOfWeek): Int =
        weekDates(today, firstDay).count { it.toEpochDay() in workoutDays }
}
