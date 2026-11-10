package com.daymark.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.daymark.app.container
import com.daymark.app.data.Settings
import com.daymark.app.domain.BuildDay
import com.daymark.app.domain.BuildEngine
import com.daymark.app.domain.Completion
import com.daymark.app.domain.DayStatus
import com.daymark.app.domain.Habit
import com.daymark.app.domain.Messages
import com.daymark.app.domain.RelapseRecord
import com.daymark.app.domain.StreakEngine
import com.daymark.app.domain.StreakStats
import com.daymark.app.domain.UrgeRecord
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Everything a widget needs to render, computed once per composition. */
data class WidgetSnapshot(
    val onboarded: Boolean,
    /** Tracker name to show on the home screen — blank in discreet mode. */
    val habit: String,
    val stats: StreakStats,
    val now: Long,
    val today: LocalDate,
    private val journeyStart: Long,
    private val relapseDates: Set<LocalDate>,
    private val earliestRelapse: Long?,
    private val zone: ZoneId,
    /** Build habits: per-day status mapping (done = clean, missed = red). Null for break habits. */
    private val buildStatus: ((LocalDate) -> DayStatus)? = null,
    val emoji: String = "",
    /** The full tracker (money, cut-down limit, reasons), or null before onboarding. */
    val tracker: Habit? = null,
    val discreet: Boolean = false,
    /** Entries logged today — "uses" in cut-down mode. */
    val todayUses: Int = 0,
    val urgeCount: Int = 0,
    val urgesLast30: Int = 0,
) {
    val isReduce: Boolean get() = tracker?.isReduce == true
    val dailyLimit: Int get() = tracker?.dailyLimit ?: 0
    val moneyKept: Double get() = tracker?.moneyKept(stats.totalCleanDays) ?: 0.0
    val tracksMoney: Boolean get() = tracker?.tracksMoney == true

    /** The user's own reasons — only ever shown on the home screen when discreet mode is off. */
    val visibleReasons: List<String> get() = if (discreet) emptyList() else tracker?.reasons.orEmpty()

    val message: String get() = Messages.forDate(today)

    /** Millis elapsed in the current streak-day — the part the live chronometer shows. */
    val intoDayMillis: Long get() = stats.currentMillis % StreakStats.DAY_MS

    fun status(date: LocalDate): DayStatus =
        buildStatus?.invoke(date) ?: StreakEngine.dayStatus(date, journeyStart, relapseDates, earliestRelapse, today, zone)

    companion object {
        /** [habit] null = no tracker yet (not onboarded). [relapses] may include other trackers; they're filtered here. */
        fun from(
            habit: Habit?,
            relapses: List<RelapseRecord>,
            now: Long = System.currentTimeMillis(),
            urges: List<UrgeRecord> = emptyList(),
            discreet: Boolean = false,
            completions: List<Completion> = emptyList(),
        ): WidgetSnapshot {
            val zone = ZoneId.systemDefault()
            val entries = if (habit == null) emptyList() else relapses.filter { it.habitId == habit.id }.map { it.timestamp }
            val ts = habit?.slipTimes(entries, zone) ?: emptyList()
            val today = StreakEngine.toDate(now, zone)
            val mineCompletions = if (habit?.isBuild == true) completions.filter { it.habitId == habit.id } else emptyList()
            val build = if (habit?.isBuild == true) BuildEngine.compute(habit.startAt, habit.scheduleDays, habit.goalTarget, mineCompletions, today, zone) else null
            val amounts = mineCompletions.associate { it.date to it.amount }
            val mine = if (habit == null) emptyList() else urges.filter { it.habitId == habit.id && it.timestamp <= now }
            return WidgetSnapshot(
                onboarded = habit != null,
                habit = if (discreet) "" else habit?.name.orEmpty(),
                stats = when {
                    habit == null -> StreakStats.EMPTY
                    habit.isBuild -> build!!.asStreakStats(now, zone)
                    else -> StreakEngine.compute(habit.startAt, ts, now, zone)
                },
                now = now,
                today = StreakEngine.toDate(now, zone),
                journeyStart = habit?.startAt ?: now,
                relapseDates = StreakEngine.relapseDates(ts, zone),
                earliestRelapse = ts.minOrNull(),
                zone = zone,
                buildStatus = if (build == null || habit == null) null else { d ->
                    when (BuildEngine.status(d, habit.scheduleDays, habit.goalTarget, amounts, build.startDate, today)) {
                        BuildDay.DONE -> DayStatus.CLEAN
                        BuildDay.MISSED -> DayStatus.RELAPSE
                        else -> DayStatus.UNTRACKED
                    }
                },
                emoji = habit?.emoji.orEmpty(),
                tracker = habit,
                discreet = discreet,
                todayUses = entries.count { StreakEngine.toDate(it, zone) == today },
                urgeCount = mine.size,
                urgesLast30 = mine.count { it.timestamp >= now - 30 * StreakStats.DAY_MS },
            )
        }

        fun resolveHabit(habits: List<Habit>, preferredId: Long?, settings: Settings): Habit? =
            habits.firstOrNull { it.id == preferredId } ?: habits.firstOrNull { it.id == settings.selectedHabitId } ?: habits.firstOrNull()
    }
}

object WidgetUpdater {
    private const val WORK = "daymark-widget-refresh"

    /** Refresh every Daymark widget, then schedule the next refresh at the next meaningful moment. */
    suspend fun updateAll(context: Context) {
        val app = context.applicationContext
        runCatching { StreakWidget().updateAll(app) }
        runCatching { RingWidget().updateAll(app) }
        runCatching { HeatmapWidget().updateAll(app) }
        runCatching { QuoteWidget().updateAll(app) }
        runCatching { ActionsWidget().updateAll(app) }
        runCatching { StatsWidget().updateAll(app) }
        runCatching { scheduleNext(app) }
    }

    /**
     * The chronometer ticks by itself, but the day count, ring and heatmap change only at
     * the next streak-day boundary or local midnight — wake up exactly then (WorkManager, no exact-alarm permission).
     */
    private suspend fun scheduleNext(context: Context) {
        val habits = context.container.habits.all()
        if (habits.isEmpty()) return
        val relapses = context.container.relapses.all()
        val comps = context.container.completions.all()
        val now = System.currentTimeMillis()
        val today = StreakEngine.toDate(now, ZoneId.systemDefault())
        val nextMidnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Earliest upcoming streak-day boundary across every tracker (any widget may show any tracker).
        val nextStreakDay = habits.minOf { h ->
            val st = WidgetSnapshot.from(h, relapses, now, completions = comps).stats
            st.currentStart + (st.currentDays + 1) * StreakStats.DAY_MS
        }
        val delay = (minOf(nextStreakDay, nextMidnight) - now).coerceIn(15_000L, StreakStats.DAY_MS) + 2_000L
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.updateAll(applicationContext)
        return Result.success()
    }
}
