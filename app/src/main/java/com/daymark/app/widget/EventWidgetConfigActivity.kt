package com.daymark.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.daymark.app.container
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.WidgetRefresh
import com.daymark.app.ui.events.EventBackground
import com.daymark.app.ui.events.EventCard
import com.daymark.app.ui.events.rememberMinuteNow
import com.daymark.app.ui.theme.Accent
import com.daymark.app.ui.theme.DaymarkTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** "Select an event": shown when an event widget is added and from "Reconfigure". */
class EventWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val glanceId = runCatching { GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId) }.getOrNull() ?: run { finish(); return }
        // Pinned from inside the app for a specific event: no picker, the new widget already knows what to show.
        if (runBlocking { claimPendingEventPin(applicationContext, glanceId) }) {
            runBlocking { EventWidget().update(applicationContext, glanceId) }
            setResult(RESULT_OK, result)
            finish()
            return
        }
        val state = runBlocking { getAppWidgetState(this@EventWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) }
        val settings0 = runBlocking { container.settings.current() }
        val initialId = state[EventIdKey] ?: 0L
        val initialQueue = state[QueueKey] == true
        val initialN = (state[QueueNKey] ?: 1).coerceIn(1, 5)
        val initialBg = backgroundOf(state[BgModeKey])

        setContent {
            DaymarkTheme(themeMode = settings0.themeMode, accent = Accent.of(settings0.accent)) {
                val events by container.events.events.collectAsState(emptyList())
                val scope = rememberCoroutineScope()
                EventWidgetConfig(
                    events = events.filter { !it.archived },
                    initialId = initialId, initialQueue = initialQueue, initialN = initialN, initialBg = initialBg,
                    onCancel = { finish() },
                    onSave = { id, queue, n, bg ->
                        scope.launch {
                            updateAppWidgetState(this@EventWidgetConfigActivity, glanceId) {
                                if (id != 0L) it[EventIdKey] = id
                                it[QueueKey] = queue
                                it[QueueNKey] = n
                                it[BgModeKey] = bg.name
                                it[RefreshKey] = WidgetRefresh.nextKey(it[RefreshKey], System.currentTimeMillis())
                            }
                            EventWidget().update(this@EventWidgetConfigActivity, glanceId)
                            runCatching { WidgetUpdater.updateAll(applicationContext) }
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    },
                )
            }
        }
    }
}

private val BG_LABELS = listOf(EventBackground.FULL to "Full color", EventBackground.TRANSLUCENT to "Translucent", EventBackground.TRANSPARENT to "Transparent")

@Composable
internal fun EventWidgetConfig(
    events: List<CountEvent>,
    initialId: Long,
    initialQueue: Boolean,
    initialN: Int,
    initialBg: EventBackground,
    onCancel: () -> Unit,
    onSave: (Long, Boolean, Int, EventBackground) -> Unit,
) {
    var selected by remember { mutableLongStateOf(initialId) }
    var queue by remember { mutableStateOf(initialQueue) }
    var n by remember { mutableIntStateOf(initialN) }
    var bg by remember { mutableStateOf(initialBg) }
    val now = rememberMinuteNow()
    val effective = selected.takeIf { id -> events.any { it.id == id } } ?: events.firstOrNull()?.id ?: 0L
    val canSave = queue || effective != 0L
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Close") }
                Text("Select an event", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onSave(effective, queue, n, bg) }, enabled = canSave) { Text("Save") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Upcoming queue", style = MaterialTheme.typography.titleMedium)
                        Text("Always show the event that finishes next", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(queue, { queue = it })
                }
                if (queue) {
                    Text("Auto select event", style = MaterialTheme.typography.titleSmall)
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(5) { i ->
                            FilterChip(
                                selected = n == i + 1, onClick = { n = i + 1 },
                                label = { Text(if (i == 0) "Nearest to finish" else "${ordinal(i + 1)} nearest") },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                } else if (events.isEmpty()) {
                    Text("You have no events yet. Create one in the Events tab, then come back.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // Fixed-height grid inside the scroll column: two columns of cards.
                    val rows = (events.size + 1) / 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().height((rows * 140 + (rows - 1) * 10).dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        userScrollEnabled = false,
                    ) {
                        items(events, key = { it.id }) { e ->
                            val sel = e.id == effective
                            Box(
                                Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(24.dp))
                                    .border(if (sel) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, androidx.compose.ui.graphics.Color.Transparent), RoundedCornerShape(24.dp))
                                    .clickable { selected = e.id },
                            ) { EventCard(e, now, Modifier.fillMaxSize().padding(if (sel) 3.dp else 0.dp), hideName = false) }
                        }
                    }
                }
                Text("Widget background", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BG_LABELS.forEach { (m, label) ->
                        FilterChip(selected = bg == m, onClick = { bg = m }, label = { Text(label) }, shape = RoundedCornerShape(12.dp))
                    }
                }
                Text(
                    "Widget not refreshing? Some phones pause apps in the background. Set Daymark's battery use to Unrestricted in system settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun ordinal(i: Int) = when (i) { 2 -> "2nd"; 3 -> "3rd"; else -> "${i}th" }
