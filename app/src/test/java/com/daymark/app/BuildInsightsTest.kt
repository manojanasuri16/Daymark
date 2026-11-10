package com.daymark.app

import com.daymark.app.domain.BuildInsights
import com.daymark.app.domain.Completion
import com.daymark.app.domain.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class BuildInsightsTest {
    private val today = LocalDate.of(2026, 11, 11) // Wednesday
    private fun c(date: LocalDate, amount: Double = 1.0) = Completion(1, date.toEpochDay(), amount)

    @Test fun streakRunsSplitOnMisses() {
        val start = today.minusDays(6)
        // done d0,d1,d2 ; missed d3 ; done d4,d5 ; today pending
        val comps = listOf(0L, 1, 2, 4, 5).map { c(start.plusDays(it)) }
        assertEquals(listOf(3, 2), BuildInsights.streakRuns(start, today, Schedule.EVERY_DAY, 0.0, comps))
    }

    @Test fun restDaysDoNotBreakRuns() {
        val weekdays = Schedule.of(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
        val fri = LocalDate.of(2026, 11, 6)
        val comps = listOf(fri, fri.plusDays(3), fri.plusDays(4)).map { c(it) } // Fri, Mon, Tue
        assertEquals(listOf(3), BuildInsights.streakRuns(fri, today.minusDays(1), weekdays, 0.0, comps))
    }

    @Test fun weekdayStatsCountDueDaysOnly() {
        val start = today.minusDays(13)
        val comps = (0L..13).map { start.plusDays(it) }.filter { it.dayOfWeek == DayOfWeek.MONDAY }.map { c(it) }
        val stats = BuildInsights.weekdayStats(start, today, Schedule.EVERY_DAY, 0.0, comps)
        assertEquals(1.0, stats[0].rate, 1e-9)
        assertEquals(0.0, stats[1].rate, 1e-9)
        assertEquals(DayOfWeek.MONDAY, BuildInsights.bestWeekday(stats))
    }

    @Test fun noBestWeekdayWithoutData() {
        val stats = BuildInsights.weekdayStats(today, today, Schedule.EVERY_DAY, 0.0, emptyList())
        assertNull(BuildInsights.bestWeekday(stats))
    }
}
