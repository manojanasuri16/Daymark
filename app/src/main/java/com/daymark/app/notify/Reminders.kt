package com.daymark.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daymark.app.R
import com.daymark.app.container
import com.daymark.app.domain.Milestones
import com.daymark.app.domain.StreakEngine
import com.daymark.app.domain.StreakStats
import com.daymark.app.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Where a notification, shortcut or tile should land inside the app. */
object Routes {
    const val EXTRA = "com.daymark.app.ROUTE"
    const val URGE = "urge"
    const val SLIP = "slip"
    const val JOURNAL = "journal"

    fun intent(context: Context, route: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            // Distinct data per route so PendingIntents for different buttons never collapse into one.
            .setData(android.net.Uri.parse("daymark://route/${route.ifEmpty { "home" }}"))
            .putExtra(EXTRA, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/**
 * Local, gentle notifications — a daily check-in and milestone celebrations. Nothing leaves the device.
 * In discreet mode (the default) the text never names a tracker, so a glance at the lock screen reveals nothing.
 * Scheduling uses WorkManager (inexact, survives reboots, needs no exact-alarm permission).
 */
object Reminders {
    private const val CH_CHECK_IN = "check_in"
    private const val CH_MILESTONES = "milestones"
    private const val WORK_CHECK_IN = "daymark-check-in"
    private const val WORK_MILESTONE = "daymark-milestone"
    private const val PREFS = "daymark_notify"
    private const val KEY_SENT = "sent_milestones"
    private const val ID_CHECK_IN = 1001
    private const val ID_MILESTONE_BASE = 2000

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_CHECK_IN, "Daily check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A gentle daily reminder to check in with yourself"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MILESTONES, "Milestones", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Celebrate when you reach a new milestone"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
    }

    /** Re-plan both kinds of notification from current settings and data. Safe to call often. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val s = app.container.settings.current()
        val wm = WorkManager.getInstance(app)

        if (s.onboarded && s.checkInReminder) {
            val zone = ZoneId.systemDefault()
            val now = LocalDateTime.now(zone)
            var next = LocalDate.now(zone).atStartOfDay().plusMinutes(s.checkInMinutes.toLong())
            if (!next.isAfter(now.plusSeconds(30))) next = next.plusDays(1)
            val delay = java.time.Duration.between(now, next).toMillis()
            wm.enqueueUniqueWork(
                WORK_CHECK_IN, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CheckInWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
            )
        } else wm.cancelUniqueWork(WORK_CHECK_IN)

        if (s.onboarded && s.milestoneAlerts) {
            val nowMs = System.currentTimeMillis()
            val habits = app.container.habits.all()
            val relapses = app.container.relapses.all()
            val nextAt = habits.mapNotNull { h ->
                val st = StreakEngine.compute(h.startAt, h.slipTimes(relapses.filter { it.habitId == h.id }.map { it.timestamp }), nowMs)
                Milestones.next(st.currentDays)?.let { st.currentStart + it.days * StreakStats.DAY_MS }
            }.minOrNull()
            if (nextAt != null) {
                val delay = (nextAt - nowMs).coerceAtLeast(0) + 60_000L
                wm.enqueueUniqueWork(
                    WORK_MILESTONE, ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<MilestoneWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
                )
            } else wm.cancelUniqueWork(WORK_MILESTONE)
        } else wm.cancelUniqueWork(WORK_MILESTONE)
    }

    private fun tap(context: Context, route: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, Routes.intent(context, route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    @android.annotation.SuppressLint("MissingPermission") // guarded by canNotify()
    internal fun postCheckIn(context: Context) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CH_CHECK_IN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to check in")
            .setContentText("How did today go? A minute of reflection helps.")
            .setContentIntent(tap(context, Routes.JOURNAL, ID_CHECK_IN))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        NotificationManagerCompat.from(context).notify(ID_CHECK_IN, n)
    }

    /** Posts any milestone reached since the last check (each one once per streak). */
    @android.annotation.SuppressLint("MissingPermission")
    internal suspend fun postMilestones(context: Context) {
        val c = context.container
        val s = c.settings.current()
        if (!s.milestoneAlerts || !canNotify(context)) return
        ensureChannels(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sent = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty().toMutableSet()
        val now = System.currentTimeMillis()
        val relapses = c.relapses.all()
        for (h in c.habits.all()) {
            val st = StreakEngine.compute(h.startAt, h.slipTimes(relapses.filter { it.habitId == h.id }.map { it.timestamp }), now)
            val m = Milestones.previous(st.currentDays) ?: continue
            // Only celebrate if it was reached in the last two days (avoids a burst after enabling the setting).
            if (st.currentDays - m.days > 1) continue
            val key = "${h.id}:${st.currentStart}:${m.days}"
            if (key in sent) continue
            sent += key
            val title = if (s.discreet) "You reached ${m.title} 🌱" else "${h.displayName}: ${m.title} 🌱"
            val n = NotificationCompat.Builder(context, CH_MILESTONES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(m.subtitle)
                .setContentIntent(tap(context, "", ID_MILESTONE_BASE + h.id.toInt()))
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            NotificationManagerCompat.from(context).notify(ID_MILESTONE_BASE + h.id.toInt(), n)
        }
        // Keep the set small.
        prefs.edit().putStringSet(KEY_SENT, sent.toList().takeLast(200).toSet()).apply()
    }
}

class CheckInWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Reminders.postCheckIn(applicationContext)
        Reminders.reschedule(applicationContext)
        return Result.success()
    }
}

class MilestoneWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Reminders.postMilestones(applicationContext)
        Reminders.reschedule(applicationContext)
        return Result.success()
    }
}
