package com.daymark.app.notify

import com.daymark.app.domain.BuildEngine
import com.daymark.app.domain.Habit
import com.daymark.app.domain.ReminderConfig
import com.daymark.app.domain.ReminderMode
import com.daymark.app.domain.Schedule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Pure planning logic for build-habit reminders (no Android types, unit-tested). */
object ReminderPlanner {
    private const val LOOKAHEAD_DAYS = 14L

    /** Minutes after midnight at which reminders fire on one day, ascending (0..1439). */
    fun slotsForDay(cfg: ReminderConfig): List<Int> = when (cfg.mode) {
        ReminderMode.NONE -> emptyList()
        ReminderMode.TIMES -> cfg.times.map { it.coerceIn(0, 1439) }.distinct().sorted()
        ReminderMode.INTERVAL -> {
            val step = cfg.intervalMinutes.coerceIn(5, 24 * 60)
            val start = cfg.activeStart.coerceIn(0, 1439)
            val end = cfg.activeEnd.coerceIn(start, 1439)
            generateSequence(start) { it + step }.takeWhile { it <= end }.toList()
        }
    }

    /** Whether [minuteOfDay] is inside the habit's interval window (always true for TIMES mode). */
    fun inActiveWindow(cfg: ReminderConfig, minuteOfDay: Int): Boolean = when (cfg.mode) {
        ReminderMode.INTERVAL -> {
            val start = cfg.activeStart.coerceIn(0, 1439)
            minuteOfDay in start..cfg.activeEnd.coerceIn(start, 1439)
        }
        ReminderMode.TIMES -> true
        ReminderMode.NONE -> false
    }

    /**
     * Next epoch-millis (strictly after [nowMillis]) at which [habit] should nudge, or null if never.
     * [todayAmount] is the progress logged for the local day containing [nowMillis]; later days start at zero.
     */
    fun nextFireTime(habit: Habit, nowMillis: Long, zone: ZoneId, todayAmount: Double): Long? {
        if (!habit.isBuild || !habit.reminder.enabled) return null
        val slots = slotsForDay(habit.reminder)
        if (slots.isEmpty()) return null
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val mask = Schedule.normalize(habit.scheduleDays)
        for (offset in 0..LOOKAHEAD_DAYS) {
            val date: LocalDate = today.plusDays(offset)
            if (!Schedule.isScheduled(mask, date)) continue
            if (offset == 0L && BuildEngine.isDone(todayAmount, habit.goalTarget)) continue
            for (m in slots) {
                val t = date.atTime(LocalTime.of(m / 60, m % 60)).atZone(zone).toInstant().toEpochMilli()
                if (t > nowMillis) return t
            }
        }
        return null
    }
}
