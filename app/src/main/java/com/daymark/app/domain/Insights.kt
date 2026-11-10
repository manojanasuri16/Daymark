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
    /** What the habit used to cost, per [spendPeriodDays] days. 0 = not tracked. */
    val spendAmount: Double = 0.0,
    val spendPeriodDays: Int = 7,
    /** 0 = quit completely. N > 0 = "cut down": a day only counts as a slip once more than N uses are logged. */
    val dailyLimit: Int = 0,
    /** The user's own reasons, shown back to them on hard days. */
    val reasons: List<String> = emptyList(),
    // --- Build-a-habit fields (ignored for BREAK habits) ---
    val kind: HabitKind = HabitKind.BREAK,
    /** Daily goal in [goalUnit]. 0 = a simple "done" check-off. */
    val goalTarget: Double = 0.0,
    val goalUnit: String = "",
    /** Quick-add increment shown on the + button (e.g. 250 for a glass of water). */
    val goalStep: Double = 1.0,
    /** See [Schedule]. */
    val scheduleDays: Int = Schedule.EVERY_DAY,
    val reminder: ReminderConfig = ReminderConfig(),
) {
    val isBuild: Boolean get() = kind == HabitKind.BUILD
    val hasGoal: Boolean get() = isBuild && goalTarget > 0
    val displayName: String get() = name.ifBlank { "My journey" }
    val isReduce: Boolean get() = dailyLimit > 0
    val tracksMoney: Boolean get() = spendAmount > 0.0 && spendPeriodDays > 0

    /** Money not spent over [cleanDays] days. */
    fun moneyKept(cleanDays: Int): Double = if (tracksMoney) cleanDays * spendAmount / spendPeriodDays else 0.0

    /** Entry timestamps that actually end a streak, given this tracker's mode. */
    fun slipTimes(entries: List<Long>, zone: ZoneId = ZoneId.systemDefault()): List<Long> =
        StreakEngine.slipTimestamps(entries, dailyLimit, zone)
}

/** A craving the user rode out without slipping. */
data class UrgeRecord(
    val id: Long,
    val habitId: Long,
    val timestamp: Long,
    /** 1..5, 0 = not recorded. */
    val intensity: Int = 0,
    val triggers: List<String> = emptyList(),
    val notes: String = "",
)

/** A free-form journal entry, on any day. */
data class JournalEntry(
    val id: Long,
    val habitId: Long,
    val timestamp: Long,
    /** 1..5, 0 = not recorded. */
    val mood: Int = 0,
    val text: String = "",
    /** File names of attached photos (in Daymark's private storage), in display order. */
    val photos: List<String> = emptyList(),
)

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
    val urgeCount: Int = 0,
    val urgesLast30: Int = 0,
    val urgeTriggers: List<Pair<String, Int>> = emptyList(),
    val urgeTimeOfDay: List<Pair<String, Int>> = emptyList(),
    val averageUrgeIntensity: Double? = null,
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
        urges: List<UrgeRecord> = emptyList(),
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
            urgeCount = urges.count { it.timestamp <= now },
            urgesLast30 = urges.count { it.timestamp in (now - 30 * StreakStats.DAY_MS)..now },
            urgeTriggers = urges.flatMap { it.triggers }.filter { it.isNotBlank() }
                .groupingBy { it }.eachCount().toList().sortedByDescending { it.second },
            urgeTimeOfDay = urges.groupingBy { bucket(Instant.ofEpochMilli(it.timestamp).atZone(zone).hour) }.eachCount()
                .let { m -> timeBuckets.map { it to (m[it] ?: 0) } },
            averageUrgeIntensity = urges.filter { it.intensity in 1..5 }.takeIf { it.isNotEmpty() }?.map { it.intensity }?.average(),
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
