package com.daymark.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Which widget kinds make sense for a tracker, and when the long-press tip is shown. Pure, unit-tested. */
object WidgetOffer {
    /** Break trackers get every tracker widget; build habits only the ones that read their streak and days. */
    fun kindsFor(isBuild: Boolean): List<WidgetKind> =
        if (isBuild) listOf(WidgetKind.STREAK, WidgetKind.RING, WidgetKind.HEATMAP)
        else listOf(WidgetKind.STREAK, WidgetKind.RING, WidgetKind.HEATMAP, WidgetKind.STATS, WidgetKind.QUOTE, WidgetKind.ACTIONS)

    /** The tip appears from the third dashboard open until it has been shown once (-1). */
    const val TIP_AFTER_VIEWS = 2
    fun shouldShowTip(views: Int): Boolean = views >= TIP_AFTER_VIEWS
    fun nextTipViews(views: Int): Int = if (views < 0) views else minOf(views + 1, TIP_AFTER_VIEWS)

    const val TIP_DONE = -1

    /** Quiet "hold the circle" hint: shown in at most [HINT_MAX_SESSIONS] app sessions, then never again (also once the sheet was opened). */
    const val HINT_MAX_SESSIONS = 5
    const val HINT_DONE = -1
    const val HINT_VISIBLE_MS = 8_000L
    fun shouldShowHint(shows: Int): Boolean = shows in 0 until HINT_MAX_SESSIONS
    fun nextHintShows(shows: Int): Int = if (shows < 0) shows else minOf(shows + 1, HINT_MAX_SESSIONS)

    /** Button label, e.g. "Add Streak widget". */
    fun buttonLabel(kind: WidgetKind): String = "Add ${kind.title} widget"
}

/** Decides once per process whether the hint is shown, so tab returns and recompositions never count or re-show it. */
class HintSession {
    private var started = false
    private var finished = false

    /** Returns the new stored count when this session shows the hint, or null when it must not (already decided, used up, or learned). */
    fun start(shows: Int): Int? {
        if (started) return null
        started = true
        return if (shouldShow(shows)) WidgetOffer.nextHintShows(shows) else null
    }
    private fun shouldShow(shows: Int) = WidgetOffer.shouldShowHint(shows)
    fun finish() { finished = true }
    val isFinished: Boolean get() = finished

    companion object { val process = HintSession() }
}

/** The one place that asks the launcher to pin a tracker widget already bound to a tracker (gallery and sheet). */
object HomeWidgetPinner {
    /**
     * Stores the style and tracker the new widget will claim, then requests the pin.
     * [WidgetPinnedReceiver] also writes the style and tracker into the widget once the launcher confirms.
     */
    fun pin(context: Context, kind: WidgetKind, style: WidgetStyle, habitId: Long): PinOutcome {
        val app = context.applicationContext
        if (kind == WidgetKind.EVENT) return PinOutcome.UNSUPPORTED
        WidgetDefaults.set(app, kind, style)
        if (habitId != 0L) WidgetDefaults.setHabit(app, kind, habitId)
        val manager = AppWidgetManager.getInstance(app)
        if (!manager.isRequestPinAppWidgetSupported) return PinOutcome.UNSUPPORTED
        val callback = PendingIntent.getBroadcast(
            app, kind.ordinal,
            Intent(app, WidgetPinnedReceiver::class.java)
                .putExtra(WidgetPinnedReceiver.EXTRA_STYLE, style.id)
                .putExtra(WidgetPinnedReceiver.EXTRA_KIND, kind.name)
                .putExtra(WidgetPinnedReceiver.EXTRA_HABIT, habitId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val ok = runCatching { manager.requestPinAppWidget(ComponentName(app, kind.receiver()), null, callback) }.getOrDefault(false)
        return if (ok) PinOutcome.REQUESTED else PinOutcome.UNSUPPORTED
    }
}
