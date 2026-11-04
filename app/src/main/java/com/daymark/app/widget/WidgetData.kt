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
import com.daymark.app.domain.DayStatus
import com.daymark.app.domain.Habit
import com.daymark.app.domain.Messages
import com.daymark.app.domain.RelapseRecord
import com.daymark.app.domain.StreakEngine
import com.daymark.app.domain.StreakStats
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Everything a widget needs to render, computed once per composition. */
data class WidgetSnapshot(
    val onboarded: Boolean,
    val habit: String,
    val stats: StreakStats,
    val now: Long,
    val today: LocalDate,
    private val journeyStart: Long,
    private val relapseDates: Set<LocalDate>,
    private val earliestRelapse: Long?,
    private val zone: ZoneId,
    val emoji: String = "",
) {
    val message: String get() = Messages.forDate(today)

    /** Millis elapsed in the current streak-day — the part the live chronometer shows. */
    val intoDayMillis: Long get() = stats.currentMillis % StreakStats.DAY_MS

    fun status(date: LocalDate): DayStatus =
        StreakEngine.dayStatus(date, journeyStart, relapseDates, earliestRelapse, today, zone)

    companion object {
        /** [habit] null = no tracker yet (not onboarded). [relapses] may include other trackers; they're filtered here. */
        fun from(habit: Habit?, relapses: List<RelapseRecord>, now: Long = System.currentTimeMillis()): WidgetSnapshot {
            val zone = ZoneId.systemDefault()
            val ts = if (habit == null) emptyList() else relapses.filter { it.habitId == habit.id }.map { it.timestamp }
            return WidgetSnapshot(
                onboarded = habit != null,
                habit = habit?.name.orEmpty(),
                stats = if (habit != null) StreakEngine.compute(habit.startAt, ts, now, zone) else StreakStats.EMPTY,
                now = now,
                today = StreakEngine.toDate(now, zone),
                journeyStart = habit?.startAt ?: now,
                relapseDates = StreakEngine.relapseDates(ts, zone),
                earliestRelapse = ts.minOrNull(),
                zone = zone,
                emoji = habit?.emoji.orEmpty(),
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
        val now = System.currentTimeMillis()
        val today = StreakEngine.toDate(now, ZoneId.systemDefault())
        val nextMidnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Earliest upcoming streak-day boundary across every tracker (any widget may show any tracker).
        val nextStreakDay = habits.minOf { h ->
            val st = WidgetSnapshot.from(h, relapses, now).stats
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
