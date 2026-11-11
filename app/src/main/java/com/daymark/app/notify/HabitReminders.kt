package com.daymark.app.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.daymark.app.R
import com.daymark.app.container
import com.daymark.app.domain.BuildEngine
import com.daymark.app.domain.Habit
import com.daymark.app.domain.ReminderMode
import com.daymark.app.domain.Schedule
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Local reminders for build habits. One inexact (setAndAllowWhileIdle, no exact-alarm permission) alarm per habit;
 * each alarm computes only the next fire time. Nothing leaves the device.
 */
object HabitReminders {
    private const val CHANNEL = "habit_reminders"
    private const val PREFS = "daymark_habit_reminders"
    private const val KEY_IDS = "scheduled_ids"
    private const val NOTIF_BASE = 3000
    private const val STALE_MS = 90 * 60_000L
    private const val SNOOZE_MS = 15 * 60_000L

    internal const val ACTION_FIRE = "com.daymark.app.HABIT_FIRE"
    internal const val ACTION_ADD = "com.daymark.app.HABIT_ADD"
    internal const val ACTION_DONE = "com.daymark.app.HABIT_DONE"
    internal const val ACTION_SNOOZE = "com.daymark.app.HABIT_SNOOZE"
    internal const val ACTION_SKIP = "com.daymark.app.HABIT_SKIP"
    internal const val EXTRA_HABIT = "habit_id"
    internal const val EXTRA_PLANNED = "planned_at"
    internal const val EXTRA_SNOOZE = "snooze"

    /** Pure: next fire time (epoch millis) for [habit], or null. */
    fun nextFireTime(habit: Habit, nowMillis: Long, zone: ZoneId, todayAmount: Double): Long? =
        ReminderPlanner.nextFireTime(habit, nowMillis, zone, todayAmount)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Opens the system battery-optimisation list (no special permission), falling back to this app's details page. */
    fun batteryOptimizationIntent(context: Context): Intent {
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        val intent = if (list.resolveActivity(context.packageManager) != null) list else details
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // ---- scheduling -------------------------------------------------------------------------------------------

    private fun code(habitId: Long, slot: Int) = (habitId.toInt() and 0x0FFFFFFF) * 8 + slot

    private fun receiverIntent(context: Context, action: String, habitId: Long) =
        Intent(action).setComponent(ComponentName(context, HabitReminderReceiver::class.java)).putExtra(EXTRA_HABIT, habitId)

    private fun broadcast(context: Context, action: String, habitId: Long, slot: Int, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context, code(habitId, slot), receiverIntent(context, action, habitId).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun firePending(context: Context, habitId: Long, plannedAt: Long) =
        broadcast(context, ACTION_FIRE, habitId, 0) { putExtra(EXTRA_PLANNED, plannedAt) }

    private fun snoozePending(context: Context, habitId: Long, plannedAt: Long) =
        broadcast(context, ACTION_FIRE, habitId, 1) { putExtra(EXTRA_PLANNED, plannedAt); putExtra(EXTRA_SNOOZE, true) }

    private fun cancelAlarm(context: Context, habitId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(firePending(context, habitId, 0))
        am.cancel(snoozePending(context, habitId, 0))
    }

    private fun setAlarm(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Re-plan every build habit's next reminder from current data. Cheap and idempotent; never posts a notification. */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone).toEpochDay()
        val habits = app.container.habits.all()
        val active = mutableSetOf<Long>()
        for (h in habits) {
            if (!h.isBuild || !h.reminder.enabled) continue
            val next = nextFireTime(h, now, zone, app.container.completions.amount(h.id, today))
            if (next == null) continue
            active += h.id
            setAlarm(app, next, firePending(app, h.id, next))
        }
        val previous = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        (previous.toSet() + habits.map { it.id }.toSet() - active).forEach { cancelAlarm(app, it) }
        prefs.edit().putStringSet(KEY_IDS, active.map { it.toString() }.toSet()).apply()
    }

    // ---- notifications ----------------------------------------------------------------------------------------

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Gentle nudges for the habits you're building"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun fmt(d: Double): String =
        if (d == Math.rint(d)) d.toLong().toString() else String.format(java.util.Locale.US, "%.1f", d)

    private fun withUnit(d: Double, unit: String) = if (unit.isBlank()) fmt(d) else "${fmt(d)} ${unit.trim()}"

    @SuppressLint("MissingPermission") // guarded by canNotify()
    private fun post(context: Context, h: Habit, amount: Double, discreet: Boolean, celebrate: Boolean = false) {
        if (!Reminders.canNotify(context)) return
        ensureChannel(context)
        val id = NOTIF_BASE + h.id.toInt()
        val tap = PendingIntent.getActivity(
            context, id, Routes.intent(context, ""), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        if (celebrate) {
            b.setContentTitle(if (discreet) "Goal reached 🎉" else "${h.emoji} ${h.displayName}")
                .setContentText(if (discreet) "Nicely done today." else "Goal reached 🎉 Nicely done.")
                .setTimeoutAfter(8_000)
        } else {
            val title = if (discreet) "A gentle nudge" else "${h.emoji} ${h.displayName}".trim()
            val text = when {
                discreet -> "Your habit check-in"
                h.hasGoal -> "${fmt(amount)} of ${withUnit(h.goalTarget, h.goalUnit)} so far"
                else -> "Ready when you are."
            }
            b.setContentTitle(title).setContentText(text)
            if (h.hasGoal) {
                val step = if (h.goalStep > 0) h.goalStep else 1.0
                b.addAction(0, "+${withUnit(step, h.goalUnit)}", broadcast(context, ACTION_ADD, h.id, 2))
            } else b.addAction(0, "Done", broadcast(context, ACTION_DONE, h.id, 2))
            b.addAction(0, "Snooze 15 min", broadcast(context, ACTION_SNOOZE, h.id, 3))
            b.addAction(0, "Skip", broadcast(context, ACTION_SKIP, h.id, 4))
        }
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    private fun cancelNotification(context: Context, habitId: Long) =
        NotificationManagerCompat.from(context).cancel(NOTIF_BASE + habitId.toInt())

    internal suspend fun handle(context: Context, intent: Intent) {
        val app = context.applicationContext
        val habitId = intent.getLongExtra(EXTRA_HABIT, -1L)
        if (habitId < 0) return
        val c = app.container
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone).toEpochDay()
        val habit = c.habits.all().firstOrNull { it.id == habitId }
        if (habit == null || !habit.isBuild) { cancelNotification(app, habitId); cancelAlarm(app, habitId); return }
        val discreet = c.settings.current().discreet

        when (intent.action) {
            ACTION_FIRE -> {
                val planned = intent.getLongExtra(EXTRA_PLANNED, now)
                val snoozed = intent.getBooleanExtra(EXTRA_SNOOZE, false)
                val amount = c.completions.amount(habitId, today)
                val local = Instant.ofEpochMilli(now).atZone(zone)
                val due = habit.reminder.enabled &&
                    Schedule.isScheduled(habit.scheduleDays, local.toLocalDate()) &&
                    !BuildEngine.isDone(amount, habit.goalTarget) &&
                    (snoozed || (now - planned <= STALE_MS &&
                        (habit.reminder.mode != ReminderMode.INTERVAL ||
                            ReminderPlanner.inActiveWindow(habit.reminder, local.hour * 60 + local.minute))))
                if (due) post(app, habit, amount, discreet)
                // A snooze never changes the regular plan; a regular alarm plans the one after it.
                if (!snoozed) {
                    val next = nextFireTime(habit, maxOf(now, planned), zone, amount)
                    if (next != null) setAlarm(app, next, firePending(app, habitId, next))
                }
            }
            ACTION_ADD -> {
                val step = if (habit.goalStep > 0) habit.goalStep else 1.0
                val total = c.completions.add(habitId, today, step)
                if (BuildEngine.isDone(total, habit.goalTarget)) {
                    post(app, habit, total, discreet, celebrate = true)
                    runCatching { Reminders.postMilestones(app) }
                } else post(app, habit, total, discreet)
                rescheduleAll(app)
            }
            ACTION_DONE -> {
                if (c.completions.amount(habitId, today) < BuildEngine.effectiveTarget(habit.goalTarget)) {
                    c.completions.set(habitId, today, BuildEngine.effectiveTarget(habit.goalTarget))
                }
                cancelNotification(app, habitId)
                runCatching { Reminders.postMilestones(app) }
                rescheduleAll(app)
            }
            ACTION_SNOOZE -> {
                cancelNotification(app, habitId)
                val at = now + SNOOZE_MS
                setAlarm(app, at, snoozePending(app, habitId, at))
            }
            ACTION_SKIP -> {
                cancelNotification(app, habitId)
                rescheduleAll(app)
            }
        }
    }
}

/** Handles reminder alarms and the notification buttons (works without opening the app). */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        app.container.appScope.launch {
            try { runCatching { HabitReminders.handle(app, intent) } } finally { pending.finish() }
        }
    }
}

/** Re-plans reminders after reboot, app update and clock/timezone changes (alarms are lost or stale then). */
class HabitBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        app.container.appScope.launch {
            try {
                runCatching { HabitReminders.rescheduleAll(app) }
                runCatching { EventNotifications.reschedule(app) }
            } finally { pending.finish() }
        }
    }
}
