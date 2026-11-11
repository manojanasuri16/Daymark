package com.daymark.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.daymark.app.R
import com.daymark.app.container
import com.daymark.app.domain.EventNotificationPlanner
import java.util.concurrent.TimeUnit

/**
 * Local notifications for events (completed, one day / week before, every day/week/month/year after start).
 * Only the NEXT due notification is scheduled, as an inexact WorkManager job (no exact-alarm permission); it is
 * re-planned on app start, boot, clock/timezone change, and whenever events or the switches change.
 */
object EventNotifications {
    private const val CH = "events"
    private const val WORK = "daymark-event-notification"
    private const val PREFS = "daymark_notify"
    private const val KEY_SENT = "sent_event_notifs"
    private const val ID_BASE = 3_000_000

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH, "Events", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Countdowns finishing and time-since milestones"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun sentSet(context: Context): MutableSet<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_SENT, emptySet()).orEmpty().toMutableSet()

    /** Plan the next due notification (or cancel when nothing is switched on). Safe to call often. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val wm = WorkManager.getInstance(app)
        val s = app.container.settings.current()
        val now = System.currentTimeMillis()
        val sent = sentSet(app)
        val at = EventNotificationPlanner.nextTime(app.container.events.all(), now, { s.notifies(it) }, { it in sent })
        if (at == null) {
            wm.cancelUniqueWork(WORK)
            return
        }
        wm.enqueueUniqueWork(
            WORK, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<EventNotificationWorker>().setInitialDelay((at - now).coerceAtLeast(0), TimeUnit.MILLISECONDS).build(),
        )
    }

    /** Shows whatever has fallen due (recent only), remembers it, and plans the next one. */
    @android.annotation.SuppressLint("MissingPermission") // guarded by Reminders.canNotify()
    internal suspend fun fire(context: Context) {
        val app = context.applicationContext
        val s = app.container.settings.current()
        val events = app.container.events.all()
        val now = System.currentTimeMillis()
        val sent = sentSet(app)
        val due = EventNotificationPlanner.due(events, now, { s.notifies(it) }, { it in sent })
        if (due.isNotEmpty()) {
            val canShow = Reminders.canNotify(app)
            if (canShow) ensureChannel(app)
            for (p in due) {
                sent += p.key // remembered even when blocked, so enabling permission later never floods old ones
                if (!canShow) continue
                val e = events.firstOrNull { it.id == p.eventId } ?: continue
                val text = EventNotificationPlanner.text(e, p, e.options.showNameOutside)
                val id = ID_BASE + ((p.eventId.toInt() and 0xFFFFF) * 8 + p.type.ordinal)
                val tap = PendingIntent.getActivity(
                    app, id, Routes.intent(app, Routes.event(p.eventId)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val n = NotificationCompat.Builder(app, CH)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(text.title)
                    .setContentText(text.body)
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .build()
                NotificationManagerCompat.from(app).notify(id, n)
            }
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY_SENT, sent.toList().takeLast(300).toSet()).apply()
        }
        reschedule(app)
    }
}

class EventNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EventNotifications.fire(applicationContext)
        return Result.success()
    }
}
