package com.daymark.app

import com.daymark.app.domain.Habit
import com.daymark.app.domain.HabitKind
import com.daymark.app.domain.ReminderConfig
import com.daymark.app.domain.ReminderMode
import com.daymark.app.domain.Schedule
import com.daymark.app.notify.ReminderPlanner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPlannerTest {
    private val zone = ZoneId.of("UTC")
    // 2025-01-06 is a Monday.
    private val monday = LocalDate.of(2025, 1, 6)

    private fun ms(d: LocalDate, h: Int, m: Int = 0) =
        LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    private fun water(mask: Int = Schedule.EVERY_DAY, interval: Int = 60) = Habit(
        id = 1, name = "Water", emoji = "x", startAt = 0, kind = HabitKind.BUILD, goalTarget = 2000.0, goalUnit = "ml",
        scheduleDays = mask,
        reminder = ReminderConfig(ReminderMode.INTERVAL, interval, 8 * 60, 21 * 60),
    )

    private fun reading(mask: Int = Schedule.EVERY_DAY) = Habit(
        id = 2, name = "Read", emoji = "x", startAt = 0, kind = HabitKind.BUILD, scheduleDays = mask,
        reminder = ReminderConfig(ReminderMode.TIMES, times = listOf(20 * 60, 7 * 60)),
    )

    @Test fun intervalSlotsInsideWindow() {
        assertEquals(ms(monday, 8), ReminderPlanner.nextFireTime(water(), ms(monday, 6), zone, 0.0))
        assertEquals(ms(monday, 11), ReminderPlanner.nextFireTime(water(interval = 90), ms(monday, 9, 31), zone, 0.0))
        assertEquals(ms(monday, 11), ReminderPlanner.nextFireTime(water(), ms(monday, 10, 0), zone, 0.0))
    }

    @Test fun nothingAfterActiveEnd() {
        assertEquals(ms(monday.plusDays(1), 8), ReminderPlanner.nextFireTime(water(), ms(monday, 21, 0), zone, 0.0))
    }

    @Test fun skipsUnscheduledDays() {
        val mwf = Schedule.of(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertEquals(ms(monday.plusDays(2), 8), ReminderPlanner.nextFireTime(water(mwf), ms(monday, 22), zone, 0.0))
    }

    @Test fun skipsWhenDoneToday() {
        assertEquals(ms(monday.plusDays(1), 8), ReminderPlanner.nextFireTime(water(), ms(monday, 12), zone, 2000.0))
    }

    @Test fun timesPicksNextTime() {
        assertEquals(ms(monday, 7), ReminderPlanner.nextFireTime(reading(), ms(monday, 6), zone, 0.0))
        assertEquals(ms(monday, 20), ReminderPlanner.nextFireTime(reading(), ms(monday, 7), zone, 0.0))
        assertEquals(ms(monday.plusDays(1), 7), ReminderPlanner.nextFireTime(reading(), ms(monday, 20, 1), zone, 0.0))
    }

    @Test fun midnightRollover() {
        val justBefore = ms(monday, 23, 59)
        assertEquals(ms(monday.plusDays(1), 7), ReminderPlanner.nextFireTime(reading(), justBefore, zone, 0.0))
        // Done yesterday does not leak into the next day.
        assertEquals(ms(monday.plusDays(1), 8), ReminderPlanner.nextFireTime(water(), justBefore, zone, 5000.0))
    }

    @Test fun disabledOrBreakIsNull() {
        assertNull(ReminderPlanner.nextFireTime(water().copy(reminder = ReminderConfig()), 0, zone, 0.0))
        assertNull(ReminderPlanner.nextFireTime(water().copy(kind = HabitKind.BREAK), 0, zone, 0.0))
    }
}
