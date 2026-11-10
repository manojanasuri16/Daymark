package com.daymark.app.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** How one weekday has gone for a build habit. */
data class WeekdayStat(val day: DayOfWeek, val scheduled: Boolean, val due: Int, val done: Int) {
    val rate: Double get() = if (due == 0) 0.0 else done.toDouble() / due
}

/** Pure helpers behind the build-habit Insights screen. */
object BuildInsights {
    private fun amounts(completions: List<Completion>): Map<LocalDate, Double> =
        completions.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.amount } }

    /** Monday..Sunday. Only scheduled days that have come due count; today counts only once done. */
    fun weekdayStats(
        startDate: LocalDate,
        today: LocalDate,
        mask: Int,
        target: Double,
        completions: List<Completion>,
    ): List<WeekdayStat> {
        val m = Schedule.normalize(mask)
        val a = amounts(completions)
        val due = IntArray(7)
        val done = IntArray(7)
        var d = startDate
        while (!d.isAfter(today)) {
            if (Schedule.isScheduled(m, d)) {
                val i = d.dayOfWeek.value - 1
                if (BuildEngine.isDone(a[d] ?: 0.0, target)) { done[i]++; due[i]++ } else if (d != today) due[i]++
            }
            d = d.plusDays(1)
        }
        return DayOfWeek.entries.map { WeekdayStat(it, Schedule.isScheduled(m, it), due[it.value - 1], done[it.value - 1]) }
    }

    /** The scheduled weekday with the best completion rate; needs at least [minDue] due days to say anything. */
    fun bestWeekday(stats: List<WeekdayStat>, minDue: Int = 2): DayOfWeek? =
        stats.filter { it.scheduled && it.due >= minDue }.maxByOrNull { it.rate }
            ?.takeIf { best -> stats.any { it.scheduled && it.due >= minDue && it.rate < best.rate } || stats.count { it.scheduled && it.due >= minDue } == 1 }
            ?.day

    /** Lengths of every run of completed scheduled days, oldest first. The last one may be the current streak. */
    fun streakRuns(
        startDate: LocalDate,
        today: LocalDate,
        mask: Int,
        target: Double,
        completions: List<Completion>,
    ): List<Int> {
        val m = Schedule.normalize(mask)
        val a = amounts(completions)
        val runs = mutableListOf<Int>()
        var cur = 0
        var d = startDate
        while (!d.isAfter(today)) {
            if (Schedule.isScheduled(m, d)) {
                if (BuildEngine.isDone(a[d] ?: 0.0, target)) cur++
                else if (d != today) { if (cur > 0) runs += cur; cur = 0 }
            }
            d = d.plusDays(1)
        }
        if (cur > 0) runs += cur
        return runs
    }
}
