package com.daymark.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A single clean run between two events (journey start / relapse). [end] is null for the live streak. */
data class StreakSpan(val start: Long, val end: Long?) {
    fun durationMillis(now: Long): Long = ((end ?: now) - start).coerceAtLeast(0)
    val isCurrent: Boolean get() = end == null
}

enum class DayStatus { CLEAN, RELAPSE, UNTRACKED }

data class StreakStats(
    val journeyStart: Long,
    val currentStart: Long,
    val currentMillis: Long,
    val longestMillis: Long,
    val longestIsCurrent: Boolean,
    val totalCleanDays: Int,
    val relapseCount: Int,
    val streaks: List<StreakSpan>,
) {
    val currentDays: Long get() = currentMillis / DAY_MS
    val longestDays: Long get() = longestMillis / DAY_MS

    companion object {
        const val DAY_MS = 86_400_000L
        val EMPTY = StreakStats(0, 0, 0, 0, true, 0, 0, emptyList())
    }
}

/**
 * Pure, deterministic streak maths. All history is preserved: a relapse only ends the
 * current span — it never erases the longest streak, total clean days or achievements.
 */
object StreakEngine {

    /** The effective beginning of tracking: the journey start, or an earlier logged relapse. */
    fun effectiveStart(journeyStart: Long, relapses: List<Long>): Long =
        relapses.minOrNull()?.let { minOf(it, journeyStart) } ?: journeyStart

    fun spans(journeyStart: Long, relapses: List<Long>, now: Long): List<StreakSpan> {
        val start = effectiveStart(journeyStart, relapses)
        val events = relapses.filter { it <= now }.sorted()
        val result = ArrayList<StreakSpan>(events.size + 1)
        var cursor = start
        for (r in events) {
            if (r > cursor) result += StreakSpan(cursor, r)
            cursor = maxOf(cursor, r)
        }
        result += StreakSpan(cursor, null)
        return result
    }

    fun compute(
        journeyStart: Long,
        relapses: List<Long>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): StreakStats {
        val spans = spans(journeyStart, relapses, now)
        val current = spans.last()
        val currentMillis = current.durationMillis(now)
        val longestPast = spans.dropLast(1).maxOfOrNull { it.durationMillis(now) } ?: 0L
        val longest = maxOf(longestPast, currentMillis)
        return StreakStats(
            journeyStart = effectiveStart(journeyStart, relapses),
            currentStart = current.start,
            currentMillis = currentMillis,
            longestMillis = longest,
            longestIsCurrent = currentMillis >= longestPast,
            totalCleanDays = totalCleanDays(journeyStart, relapses, now, zone),
            relapseCount = relapses.count { it <= now },
            streaks = spans,
        )
    }

    /** Number of calendar days, from the first tracked day through today, with no relapse logged. */
    fun totalCleanDays(journeyStart: Long, relapses: List<Long>, now: Long, zone: ZoneId): Int {
        val startDate = toDate(effectiveStart(journeyStart, relapses), zone)
        val today = toDate(now, zone)
        if (today.isBefore(startDate)) return 0
        val trackedDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, today).toInt() + 1
        val relapseDays = relapseDates(relapses, zone).count { !it.isBefore(startDate) && !it.isAfter(today) }
        return (trackedDays - relapseDays).coerceAtLeast(0)
    }

    fun relapseDates(relapses: List<Long>, zone: ZoneId): Set<LocalDate> =
        relapses.mapTo(HashSet()) { toDate(it, zone) }

    fun dayStatus(
        date: LocalDate,
        journeyStart: Long,
        relapseDates: Set<LocalDate>,
        earliestRelapse: Long?,
        today: LocalDate,
        zone: ZoneId,
    ): DayStatus {
        val start = toDate(earliestRelapse?.let { minOf(it, journeyStart) } ?: journeyStart, zone)
        return when {
            date in relapseDates -> DayStatus.RELAPSE
            date.isBefore(start) || date.isAfter(today) -> DayStatus.UNTRACKED
            else -> DayStatus.CLEAN
        }
    }

    /**
     * "Cut down" mode: a day becomes a slip only when it has more than [dailyLimit] entries; the entry that
     * crosses the limit is the slip moment. [dailyLimit] 0 = quit mode, where every entry is a slip.
     */
    fun slipTimestamps(entries: List<Long>, dailyLimit: Int, zone: ZoneId = ZoneId.systemDefault()): List<Long> {
        if (dailyLimit <= 0) return entries
        return entries.groupBy { toDate(it, zone) }.values.mapNotNull { day -> day.sorted().getOrNull(dailyLimit) }.sorted()
    }

    fun toDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
