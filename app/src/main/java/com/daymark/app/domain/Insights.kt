package com.daymark.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class RelapseRecord(
    val id: Long,
    val timestamp: Long,
    val triggers: List<String>,
    val mood: Int,
    val situation: String,
    val notes: String,
    val habitId: Long = 1,
)

/** Something the user is tracking (e.g. alcohol, smoking). Each has its own start, streaks and history. */
data class Habit(
    val id: Long,
    val name: String,
    val emoji: String,
    val startAt: Long,
    val sortOrder: Int = 0,
) {
    val displayName: String get() = name.ifBlank { "My journey" }
}

data class InsightsData(
    val monthlyRelapses: List<Pair<YearMonth, Int>>,
    val topTriggers: List<Pair<String, Int>>,
    val situations: List<Pair<String, Int>>,
    val timeOfDay: List<Pair<String, Int>>,
    val weekdays: List<Pair<DayOfWeek, Int>>,
    val moodCounts: List<Pair<Int, Int>>,
    val averageMood: Double?,
    val streakHistoryDays: List<Double>,
    val averageStreakDays: Double,
    val trend: Trend,
) {
    enum class Trend { IMPROVING, STEADY, HARDER, NOT_ENOUGH_DATA }
}

object InsightsEngine {
    val timeBuckets = listOf("Morning", "Afternoon", "Evening", "Night")

    fun bucket(hour: Int): String = when (hour) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..21 -> "Evening"
        else -> "Night"
    }

    fun compute(
        records: List<RelapseRecord>,
        spans: List<StreakSpan>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        months: Int = 6,
    ): InsightsData {
        val thisMonth = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val byMonth = records.groupingBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }.eachCount()
        val monthly = (months - 1 downTo 0).map { thisMonth.minusMonths(it.toLong()) }.map { it to (byMonth[it] ?: 0) }

        val triggers = records.flatMap { it.triggers }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

        val situations = records.map { it.situation }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

        val tod = records.groupingBy { bucket(Instant.ofEpochMilli(it.timestamp).atZone(zone).hour) }.eachCount()
        val weekdayCounts = records.groupingBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfWeek }.eachCount()

        val moods = records.filter { it.mood in 1..5 }
        val moodCounts = (1..5).map { m -> m to moods.count { it.mood == m } }

        val history = spans.map { it.durationMillis(now) / StreakStats.DAY_MS.toDouble() }
        val completed = spans.filter { !it.isCurrent }.map { it.durationMillis(now) / StreakStats.DAY_MS.toDouble() }

        return InsightsData(
            monthlyRelapses = monthly,
            topTriggers = triggers,
            situations = situations,
            timeOfDay = timeBuckets.map { it to (tod[it] ?: 0) },
            weekdays = DayOfWeek.entries.map { it to (weekdayCounts[it] ?: 0) },
            moodCounts = moodCounts,
            averageMood = moods.takeIf { it.isNotEmpty() }?.map { it.mood }?.average(),
            streakHistoryDays = history,
            averageStreakDays = if (history.isEmpty()) 0.0 else history.average(),
            trend = trend(completed),
        )
    }

    /** Compares the most recent half of completed streaks with the earlier half. */
    fun trend(completedDays: List<Double>): InsightsData.Trend {
        if (completedDays.size < 4) return InsightsData.Trend.NOT_ENOUGH_DATA
        val half = completedDays.size / 2
        val early = completedDays.take(half).average()
        val recent = completedDays.takeLast(half).average()
        return when {
            recent > early * 1.15 -> InsightsData.Trend.IMPROVING
            recent < early * 0.85 -> InsightsData.Trend.HARDER
            else -> InsightsData.Trend.STEADY
        }
    }
}
