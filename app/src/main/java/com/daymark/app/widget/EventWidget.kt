package com.daymark.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.daymark.app.container
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventEngine
import com.daymark.app.notify.Routes
import com.daymark.app.ui.events.EventArt
import com.daymark.app.ui.events.EventBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val EVENT_PAD = 4.dp

/** Which event a widget shows: the chosen one, or in queue mode the Nth nearest to finish. Pure, so it re-resolves on every refresh. */
internal fun resolveWidgetEvent(events: List<CountEvent>, now: Long, eventId: Long?, queue: Boolean, queueN: Int): CountEvent? =
    if (queue) EventEngine.nextEvent(events, now, (queueN.coerceIn(1, 5)) - 1)
    else events.firstOrNull { it.id == eventId }

internal fun backgroundOf(id: String?): EventBackground = EventBackground.entries.firstOrNull { it.name == id } ?: EventBackground.FULL

/**
 * Event widget: [EventArt] drawn as ONE Fit-scaled bitmap per Responsive breakpoint (the same pixels as the editor
 * preview). The state keys (EventIdKey, QueueKey, QueueNKey, BgModeKey) live in the widget's Glance prefs. It never
 * shows habit data, and honours the event's `showNameOutside`.
 */
class EventWidget : DaymarkGlanceWidget() {
    override val kind = WidgetKind.EVENT
    override val sizes: Set<DpSize> = setOf(
        DpSize(110.dp, 50.dp), DpSize(250.dp, 50.dp), DpSize(110.dp, 110.dp), DpSize(180.dp, 110.dp),
        DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(300.dp, 250.dp),
    )

    /** Unused: the event widget has its own content path ([provideEventContent]). */
    @Composable
    override fun Content(snap: WidgetSnapshot, style: WidgetStyle, prefs: Preferences) = Unit
}

private fun artSize(size: DpSize) = (size.width - EVENT_PAD * 2) to (size.height - EVENT_PAD * 2)

internal suspend fun DaymarkGlanceWidget.provideEventContent(app: Context, id: GlanceId) {
    val e0 = withContext(Dispatchers.IO) { app.container.events.all() }
    val s0 = withContext(Dispatchers.IO) { app.container.settings.current() }
    val appWidgetId = runCatching { GlanceAppWidgetManager(app).getAppWidgetId(id) }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
    val breakpoints = sizes.map { artSize(it).let { (w, h) -> w.value * h.value } }
    provideContent {
        val context = LocalContext.current
        val events by app.container.events.events.collectAsState(e0)
        val settings by app.container.settings.settings.collectAsState(s0)
        val prefs = currentState<Preferences>()
        // Read so a RefreshKey write forces a recompose; the event, text and clock are recomputed each time.
        prefs[RefreshKey]
        val now = System.currentTimeMillis()
        val event = resolveWidgetEvent(events, now, prefs[EventIdKey], prefs[QueueKey] == true, prefs[QueueNKey] ?: 1)
        val size = LocalSize.current
        val root = GlanceModifier.fillMaxSize().appWidgetBackground()
        if (event == null) {
            val config = Intent(context, EventWidgetConfigActivity::class.java)
                .setData(android.net.Uri.parse("daymark://eventwidget/$appWidgetId"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            Box(root.padding(EVENT_PAD).clickable(actionStartActivity(config)), contentAlignment = Alignment.Center) {
                Column(
                    GlanceModifier.fillMaxSize()
                        .background(ColorProvider(Color(0xFF2B2F3A)))
                        .cornerRadius(24.dp).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Choose an event", style = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 2)
                    if (size.height >= 100.dp) {
                        Text("Tap to pick what to count", style = TextStyle(color = ColorProvider(Color(0xB3FFFFFF)), fontSize = 12.sp, textAlign = TextAlign.Center), maxLines = 2)
                    }
                }
            }
        } else {
            val (wDp, hDp) = artSize(size)
            val density = artDensity(context, breakpoints.map { it })
            val bmp = EventArt.render(
                context, event, now, artPx(wDp, density), artPx(hDp, density),
                backgroundOf(prefs[BgModeKey]), density,
                hideName = !event.options.showNameOutside, precision = settings.precisionCountdown,
            )
            Box(root.padding(EVENT_PAD).clickable(actionStartActivity(Routes.intent(context, Routes.event(event.id))))) {
                Image(
                    ImageProvider(bmp),
                    contentDescription = EventArt.describe(event, now, !event.options.showNameOutside),
                    contentScale = ContentScale.Fit,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
        }
    }
}
