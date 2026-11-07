package com.daymark.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.daymark.app.container
import com.daymark.app.domain.Habit
import com.daymark.app.domain.RelapseRecord
import com.daymark.app.ui.theme.Accent
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.theme.DaymarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Shown when a widget is added (and from "Reconfigure" on Android 12+): pick one of the styles. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val kind = kindForProvider(AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className)
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        val state = runBlocking { getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId) }
        val initial = WidgetStyle.of(state[StyleKey])
        val settings0 = runBlocking { container.settings.current() }
        val initialHabit = state[HabitKey] ?: settings0.selectedHabitId

        setContent {
            DaymarkTheme(themeMode = settings0.themeMode, accent = Accent.of(settings0.accent)) {
                val habits by container.habits.habits.collectAsState(null)
                val relapses by container.relapses.relapses.collectAsState(emptyList())
                val scope = rememberCoroutineScope()
                StylePicker(
                    kind = kind,
                    initial = initial,
                    habits = habits.orEmpty(),
                    initialHabitId = initialHabit,
                    relapses = relapses,
                    discreet = settings0.discreet,
                    onCancel = { finish() },
                    onDone = { style, habitId ->
                        scope.launch {
                            updateAppWidgetState(this@WidgetConfigActivity, glanceId) {
                                it[StyleKey] = style.id
                                if (habitId != null) it[HabitKey] = habitId
                            }
                            kind.widget().update(this@WidgetConfigActivity, glanceId)
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun StylePicker(
    kind: WidgetKind,
    initial: WidgetStyle,
    habits: List<Habit>,
    initialHabitId: Long,
    relapses: List<RelapseRecord>,
    discreet: Boolean = true,
    onCancel: () -> Unit,
    onDone: (WidgetStyle, Long?) -> Unit,
    confirmLabel: String = "Done",
) {
    var style by remember { mutableStateOf(initial) }
    var habitId by remember { mutableStateOf(initialHabitId) }
    val habit = habits.firstOrNull { it.id == habitId } ?: habits.firstOrNull()
    val snap = habit?.let { WidgetSnapshot.from(it, relapses, discreet = discreet) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(kind.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
            Text(
                "Choose a style for this widget. You can change it any time.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            AnimatedContent(style, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "preview") { s ->
                WidgetPreview(
                    kind, s, snap,
                    Modifier.fillMaxWidth(if (kind == WidgetKind.RING) 0.6f else 1f).widthIn(max = 420.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WidgetStyle.entries.forEach { s ->
                    val src = remember { MutableInteractionSource() }
                    Column(
                        Modifier.pressScale(src, 0.92f).clickable(src, null) { style = s },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StyleSwatch(s, s == style, MaterialTheme.colorScheme.primary)
                        Text(s.label, style = MaterialTheme.typography.labelMedium,
                            color = if (s == style) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (habits.size > 1) {
                Spacer(Modifier.height(20.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(habits, key = { it.id }) { h ->
                        FilterChip(
                            selected = h.id == habit?.id,
                            onClick = { habitId = h.id },
                            label = { Text("${h.emoji}  ${h.displayName}") },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Button(onClick = { onDone(style, habit?.id) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
                Text(confirmLabel, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/** Receives the callback after an in-app "Add to home screen" pin request and applies the chosen style. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val style = intent.getStringExtra(EXTRA_STYLE) ?: return
        val habitId = intent.getLongExtra(EXTRA_HABIT, 0L)
        val kind = runCatching { WidgetKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "") }.getOrNull() ?: return
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
                updateAppWidgetState(context, glanceId) { prefs: androidx.datastore.preferences.core.MutablePreferences -> prefs[StyleKey] = style; if (habitId != 0L) prefs[HabitKey] = habitId }
                kind.widget().update(context, glanceId)
            } finally { pending.finish() }
        }
    }

    companion object {
        const val EXTRA_STYLE = "daymark.style"
        const val EXTRA_KIND = "daymark.kind"
        const val EXTRA_HABIT = "daymark.habit"
    }
}
