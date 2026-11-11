package com.daymark.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What a freshly pinned Event widget should show: one event, or (eventId == null) the Nth event nearest to finish.
 * Pure, so the claim/expiry rules are unit-tested.
 */
data class PendingEventPin(val eventId: Long?, val queueN: Int = 1, val createdAt: Long) {
    val queue: Boolean get() = eventId == null

    /** Still worth applying: requested at most [TTL_MS] ago (and not from the future). */
    fun isLive(now: Long): Boolean = now - createdAt in 0..TTL_MS

    companion object {
        const val TTL_MS = 2 * 60_000L

        /** The pin a widget may adopt, or null: it must be live and the widget must not have been configured yet. */
        fun claimable(pin: PendingEventPin?, now: Long, alreadyConfigured: Boolean): PendingEventPin? =
            pin?.takeIf { !alreadyConfigured && it.isLive(now) }
    }
}

/** Remembers the last "add to home screen" request until the new widget claims it (once). */
internal object PendingEventPinStore {
    private const val FILE = "event_pin_pending"

    fun save(context: Context, pin: PendingEventPin) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong("id", pin.eventId ?: 0L).putBoolean("has", pin.eventId != null)
            .putInt("n", pin.queueN).putLong("at", pin.createdAt).commit()
    }

    fun peek(context: Context): PendingEventPin? {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val at = p.getLong("at", 0L)
        if (at == 0L) return null
        return PendingEventPin(if (p.getBoolean("has", false)) p.getLong("id", 0L) else null, p.getInt("n", 1), at)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }
}

private fun MutablePreferences.isEventConfigured() = this[EventIdKey] != null || this[QueueKey] != null

/**
 * Writes [pin] into a widget's Glance state unless the widget was already configured (a reconfigure or a second
 * claim must never overwrite the user's choice). Returns true if it applied. Does not trigger a redraw.
 */
internal suspend fun claimPendingEventPin(context: Context, glanceId: GlanceId, now: Long = System.currentTimeMillis(), fallback: PendingEventPin? = null): Boolean {
    val pin = PendingEventPinStore.peek(context) ?: fallback
    if (pin == null) return false
    var applied = false
    updateAppWidgetState(context, glanceId) { prefs ->
        val use = PendingEventPin.claimable(pin, now, prefs.isEventConfigured()) ?: return@updateAppWidgetState
        if (use.eventId != null) prefs[EventIdKey] = use.eventId
        prefs[QueueKey] = use.queue
        prefs[QueueNKey] = use.queueN
        if (prefs[BgModeKey] == null) prefs[BgModeKey] = com.daymark.app.ui.events.EventBackground.FULL.name
        applied = true
    }
    if (applied) PendingEventPinStore.clear(context)
    return applied
}

enum class PinOutcome { REQUESTED, UNSUPPORTED }

/** The one place that asks the launcher to pin an Event widget (used by the event menus, the wizard and the gallery). */
object EventWidgetPinner {
    const val MSG_UNSUPPORTED = "Couldn't add the widget here. Long-press your home screen, choose Widgets, then Daymark."
    const val MSG_ADDED = "Widget added"

    /** [eventId] null = the "upcoming" widget (the event nearest to finish). */
    fun pinEventWidget(context: Context, eventId: Long?, onResult: (PinOutcome) -> Unit = {}) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        if (!manager.isRequestPinAppWidgetSupported) { onResult(PinOutcome.UNSUPPORTED); return }
        val pin = PendingEventPin(eventId, 1, System.currentTimeMillis())
        PendingEventPinStore.save(app, pin)
        val callback = PendingIntent.getBroadcast(
            app, 4242,
            Intent(app, EventWidgetPinnedReceiver::class.java)
                .putExtra(EventWidgetPinnedReceiver.EXTRA_EVENT, eventId ?: 0L)
                .putExtra(EventWidgetPinnedReceiver.EXTRA_QUEUE, eventId == null)
                .putExtra(EventWidgetPinnedReceiver.EXTRA_AT, pin.createdAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val ok = runCatching { manager.requestPinAppWidget(ComponentName(app, EventWidgetReceiver::class.java), Bundle.EMPTY, callback) }.getOrDefault(false)
        if (!ok) { PendingEventPinStore.clear(app); onResult(PinOutcome.UNSUPPORTED) } else onResult(PinOutcome.REQUESTED)
    }

    /** Snackbar text for an outcome, or null when the system dialog speaks for itself. */
    fun message(outcome: PinOutcome): String? = if (outcome == PinOutcome.UNSUPPORTED) MSG_UNSUPPORTED else null
}

/** Pin-success callback: points the new widget at the requested event, then redraws it. */
class EventWidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val event = intent.getLongExtra(EXTRA_EVENT, 0L)
        val pin = PendingEventPin(if (intent.getBooleanExtra(EXTRA_QUEUE, false)) null else event.takeIf { it != 0L }, 1, intent.getLongExtra(EXTRA_AT, 0L))
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(id)
                claimPendingEventPin(app, glanceId, fallback = pin)
                EventWidget().update(app, glanceId)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(app, EventWidgetPinner.MSG_ADDED, Toast.LENGTH_SHORT).show()
                }
            } finally { pending.finish() }
        }
    }

    companion object {
        const val EXTRA_EVENT = "daymark.pin.event"
        const val EXTRA_QUEUE = "daymark.pin.queue"
        const val EXTRA_AT = "daymark.pin.at"
    }
}
