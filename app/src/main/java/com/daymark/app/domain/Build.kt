package com.daymark.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Break = quit/reduce something (time since the last slip). Build = do something on a schedule (daily completions). */
enum class HabitKind(val id: Int) {
    BREAK(0), BUILD(1);

    companion object {
        fun of(id: Int) = if (id == 1) BUILD else BREAK
    }
}

/** How a build habit nudges the user. INTERVAL = every N minutes inside active hours (water); TIMES = fixed times of day (reading, gym). */
enum class ReminderMode(val id: Int) {
    NONE(0), INTERVAL(1), TIMES(2);

    companion object {
        fun of(id: Int) = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** All times are minutes after local midnight. */
data class ReminderConfig(
    val mode: ReminderMode = ReminderMode.NONE,
    val intervalMinutes: Int = 60,
    val activeStart: Int = 8 * 60,
    val activeEnd: Int = 21 * 60,
    val times: List<Int> = emptyList(),
) {
    val enabled: Boolean get() = mode != ReminderMode.NONE

    companion object {
        val INTERVAL_CHOICES = listOf(30, 45, 60, 90, 120, 180)
    }
}

/** Weekly schedule as a bitmask: bit 0 = Monday … bit 6 = Sunday. */
object Schedule {
    const val EVERY_DAY = 127

    fun isScheduled(mask: Int, day: DayOfWeek): Boolean = mask and (1 shl (day.value - 1)) != 0
    fun isScheduled(mask: Int, date: LocalDate): Boolean = isScheduled(mask, date.dayOfWeek)
    fun days(mask: Int): List<DayOfWeek> = DayOfWeek.entries.filter { isScheduled(mask, it) }
    fun of(days: Collection<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
    fun normalize(mask: Int): Int = (mask and EVERY_DAY).let { if (it == 0) EVERY_DAY else it }

    fun label(mask: Int): String {
        val m = normalize(mask)
        return when {
            m == EVERY_DAY -> "Every day"
            m == Schedule.of(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) -> "Weekdays"
            m == Schedule.of(listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) -> "Weekends"
            else -> days(m).joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
        }
    }
}

enum class BuildDay { DONE, PARTIAL, MISSED, PENDING, REST, UNTRACKED }

/** A day's logged progress for a build habit (one row per habit per day; [amount] is in the habit's unit, or 1 for a simple check-off). */
data class Completion(val habitId: Long, val epochDay: Long, val amount: Double) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

data class BuildStats(
    val startDate: LocalDate,
    val currentStreak: Int,
    val longestStreak: Int,
    val longestIsCurrent: Boolean,
    /** Scheduled days completed, plus bonus completions on rest days. */
    val totalDone: Int,
    /** Scheduled days that have come due: past scheduled days, plus today if already done. */
    val dueDays: Int,
    val missedDays: Int,
    /** 0..1 over [dueDays]. */
    val completionRate: Double,
    val last7Rate: Double,
    val last30Rate: Double,
    val todayScheduled: Boolean,
    val todayAmount: Double,
    val todayTarget: Double,
    val todayDone: Boolean,
    val totalAmount: Double,
    /** Average logged amount over days with any progress. */
    val averageAmount: Double,
    /** Completion rate of the last 8 weeks, oldest first, each starting on a Monday. */
    val weekly: List<Pair<LocalDate, Double>>,
) {
    val todayProgress: Float get() = if (todayTarget <= 0) 0f else (todayAmount / todayTarget).toFloat().coerceIn(0f, 1f)

    /** Lets screens and widgets written for streaks (days, longest, total) render a build habit sensibly. */
    fun asStreakStats(now: Long, zone: ZoneId = ZoneId.systemDefault()): StreakStats {
        val cur = currentStreak * StreakStats.DAY_MS
        return StreakStats(
            journeyStart = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            currentStart = now - cur,
            currentMillis = cur,
            longestMillis = longestStreak * StreakStats.DAY_MS,
            longestIsCurrent = longestIsCurrent,
            totalCleanDays = totalDone,
            relapseCount = missedDays,
            streaks = emptyList(),
        )
    }

    companion object {
        fun empty(today: LocalDate) = BuildStats(today, 0, 0, true, 0, 0, 0, 0.0, 0.0, 0.0, true, 0.0, 1.0, false, 0.0, 0.0, emptyList())
    }
}

/**
 * Pure streak maths for build habits. Rules:
 *  - Only scheduled days count; rest days never break a streak.
 *  - Today, if still pending, neither extends nor breaks the streak.
 *  - A miss resets the *current* streak only; longest streak, totals and history are always kept.
 */
object BuildEngine {
    fun effectiveTarget(target: Double): Double = if (target > 0) target else 1.0
    fun isDone(amount: Double, target: Double): Boolean = amount >= effectiveTarget(target) - 1e-9

    fun status(date: LocalDate, mask: Int, target: Double, amounts: Map<LocalDate, Double>, startDate: LocalDate, today: LocalDate): BuildDay {
        val amount = amounts[date] ?: 0.0
        return when {
            date.isAfter(today) || (date.isBefore(startDate) && amount <= 0) -> BuildDay.UNTRACKED
            isDone(amount, target) -> BuildDay.DONE
            !Schedule.isScheduled(mask, date) -> if (amount > 0) BuildDay.PARTIAL else BuildDay.REST
            amount > 0 -> if (date == today) BuildDay.PENDING else BuildDay.PARTIAL
            date == today -> BuildDay.PENDING
            else -> BuildDay.MISSED
        }
    }

    fun effectiveStart(startAt: Long, amounts: Map<LocalDate, Double>, zone: ZoneId): LocalDate {
        val s = StreakEngine.toDate(startAt, zone)
        return amounts.filterValues { it > 0 }.keys.minOrNull()?.let { if (it.isBefore(s)) it else s } ?: s
    }

    fun compute(
        startAt: Long,
        mask: Int,
        target: Double,
        completions: List<Completion>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BuildStats {
        val amounts = completions.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.amount } }
        val m = Schedule.normalize(mask)
        val start = effectiveStart(startAt, amounts, zone)
        if (today.isBefore(start)) return BuildStats.empty(today).copy(startDate = start, todayTarget = effectiveTarget(target))

        var cur = 0; var longest = 0; var due = 0; var doneScheduled = 0; var missed = 0; var bonus = 0
        var total = 0.0; var activeDays = 0
        var d = start
        while (!d.isAfter(today)) {
            val amount = amounts[d] ?: 0.0
            val done = isDone(amount, target)
            if (amount > 0) { total += amount; activeDays++ }
            if (Schedule.isScheduled(m, d)) {
                if (done) { cur++; longest = maxOf(longest, cur); doneScheduled++; due++ }
                else if (d != today) { cur = 0; missed++; due++ }
            } else if (done) bonus++
            d = d.plusDays(1)
        }
        val todayAmount = amounts[today] ?: 0.0
        return BuildStats(
            startDate = start,
            currentStreak = cur,
            longestStreak = longest,
            longestIsCurrent = cur >= longest,
            totalDone = doneScheduled + bonus,
            dueDays = due,
            missedDays = missed,
            completionRate = rate(start, today, today, m, target, amounts),
            last7Rate = rate(maxOf(start, today.minusDays(6)), today, today, m, target, amounts),
            last30Rate = rate(maxOf(start, today.minusDays(29)), today, today, m, target, amounts),
            todayScheduled = Schedule.isScheduled(m, today),
            todayAmount = todayAmount,
            todayTarget = effectiveTarget(target),
            todayDone = isDone(todayAmount, target),
            totalAmount = total,
            averageAmount = if (activeDays == 0) 0.0 else total / activeDays,
            weekly = weekly(start, today, m, target, amounts),
        )
    }

    /** Completed ÷ due scheduled days in [from]..[to] (today counts only once done). */
    private fun rate(from: LocalDate, to: LocalDate, today: LocalDate, mask: Int, target: Double, amounts: Map<LocalDate, Double>): Double {
        var due = 0; var done = 0
        var d = from
        while (!d.isAfter(to)) {
            if (Schedule.isScheduled(mask, d)) {
                val ok = isDone(amounts[d] ?: 0.0, target)
                if (ok) { done++; due++ } else if (d != today) due++
            }
            d = d.plusDays(1)
        }
        return if (due == 0) 0.0 else done.toDouble() / due
    }

    private fun weekly(start: LocalDate, today: LocalDate, mask: Int, target: Double, amounts: Map<LocalDate, Double>): List<Pair<LocalDate, Double>> {
        val thisMonday = today.with(DayOfWeek.MONDAY)
        return (7 downTo 0).map { thisMonday.minusWeeks(it.toLong()) }.map { monday ->
            monday to rate(maxOf(monday, start), minOf(monday.plusDays(6), today), today, mask, target, amounts)
        }
    }
}
