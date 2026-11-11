package com.daymark.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.data.Settings
import com.daymark.app.domain.NotifType
import com.daymark.app.notify.HabitReminders
import com.daymark.app.notify.Reminders
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.rememberHaptics

@Composable
private fun Footer(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun EventNotificationsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: Settings()
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    var recheck by remember { mutableIntStateOf(0) }
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterPermission?.invoke() else denied = true
        afterPermission = null
        recheck++
    }
    fun toggle(type: NotifType, on: Boolean) {
        if (!on) { vm.setEventNotification(type, false); return }
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            afterPermission = { vm.setEventNotification(type, true) }
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else vm.setEventNotification(type, true)
    }
    val anyOn = NotifType.entries.any { settings.notifies(it) }
    @Suppress("UNUSED_EXPRESSION") recheck
    val blocked = (denied || anyOn) && !Reminders.canNotify(context)

    SubScreen("Notifications", onBack) {
        if (blocked) item {
            Group {
                Column(Modifier.padding(16.dp)) {
                    Text("Notifications are blocked for Daymark", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "No problem, your events work fine without them. If you would like alerts, allow notifications for Daymark in your phone's settings.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { openAppNotificationSettings(context) }) { Text("Open notification settings") }
                }
            }
        }
        item {
            Text("Events with an end date", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        }
        item {
            Group {
                SwitchRow(Icons.Rounded.EventAvailable, "Event completed", "When the countdown or timer reaches its end", settings.eventCompleted) { toggle(NotifType.COMPLETED, it) }
                Divider()
                SwitchRow(Icons.Rounded.Today, "One day before", "A heads-up the day before it ends", settings.eventOneDayBefore) { toggle(NotifType.ONE_DAY_BEFORE, it) }
                Divider()
                SwitchRow(Icons.Rounded.DateRange, "One week before", "A heads-up a week ahead, to help you prepare", settings.eventOneWeekBefore) { toggle(NotifType.ONE_WEEK_BEFORE, it) }
            }
        }
        item { Footer("These apply to countdowns and timers. Repeating events notify again every cycle.") }
        item {
            Text("Time-since events and counting up", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
        }
        item {
            Group {
                SwitchRow(Icons.Rounded.CalendarToday, "Every day after start", "\"30 days so far\", once a day at the start time", settings.eventEveryDayAfter) { toggle(NotifType.DAILY_AFTER, it) }
                Divider()
                SwitchRow(Icons.Rounded.EventRepeat, "Every week after start", "A note each full week", settings.eventEveryWeekAfter) { toggle(NotifType.WEEKLY_AFTER, it) }
                Divider()
                SwitchRow(Icons.Rounded.CalendarMonth, "Every month after start", "A note each full month", settings.eventEveryMonthAfter) { toggle(NotifType.MONTHLY_AFTER, it) }
                Divider()
                SwitchRow(Icons.Rounded.Cake, "Every year after start", "An anniversary note each year", settings.eventEveryYearAfter) { toggle(NotifType.YEARLY_AFTER, it) }
            }
        }
        item {
            Footer(
                "These apply to events that count up. When a week or month lands on the same moment as a day, you only get the bigger one. " +
                    "Archived events never notify, and events that finished before you switched a notification on stay quiet. " +
                    "Timing is approximate (within a few minutes) so Daymark never needs exact-alarm access. " +
                    "If an event's \"show name outside the app\" is off, notifications say \"An event\" instead of its name.",
            )
        }
    }
}

@Composable
fun EventWidgetSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: Settings()
    val context = LocalContext.current
    val haptics = rememberHaptics()

    SubScreen("Event widgets", onBack) {
        item {
            Group {
                Column(Modifier.padding(16.dp)) {
                    Text("Refresh rate", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val choices = listOf(15 to "Every 15 min", 30 to "Every 30 min")
                        choices.forEachIndexed { i, (m, label) ->
                            SegmentedButton(
                                selected = (if (settings.widgetRefreshMinutes >= 30) 30 else 15) == m,
                                onClick = { haptics.tick(); vm.setWidgetRefreshMinutes(m) },
                                shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                            ) { Text(label) }
                        }
                    }
                }
            }
        }
        item {
            Footer(
                "Android lets apps refresh widgets about every 15 minutes at the fastest without draining the battery. " +
                    "Going faster would need exact alarms, which cost a lot of battery, so Daymark doesn't do that. " +
                    "Widgets also refresh at midnight and the moment an event ends.",
            )
        }
        item {
            Group {
                SwitchRow(
                    Icons.Rounded.HourglassTop, "Precision countdown",
                    "Show a smaller unit near the end, like \"36 hours left\" instead of \"1.5 days left\"",
                    settings.precisionCountdown,
                ) { vm.setPrecisionCountdown(it) }
            }
        }
        item {
            Footer("This only changes how amounts below two are worded. It never makes a widget refresh more often.")
        }
        item {
            Group {
                SettingRow(
                    Icons.Rounded.BatteryChargingFull, "Battery settings",
                    if (HabitReminders.isIgnoringBatteryOptimizations(context)) "Daymark is already unrestricted" else "Let Daymark update widgets and send alerts on time",
                    onClick = { openBatterySettings(context) },
                )
            }
        }
        item {
            Text("Widget not refreshing?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
        }
        item {
            Group {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "1. Open Battery settings above and set Daymark to Unrestricted (or \"Don't optimise\").",
                        "2. Some phones also have an \"auto-start\" or \"background activity\" list. Make sure Daymark is allowed there.",
                        "3. Don't swipe Daymark away from a \"clean up\" or \"boost\" app; those can stop widgets.",
                        "4. If a widget still looks stuck, open Daymark once, or remove the widget and add it again.",
                        "5. After a reboot or an update, the first refresh can take a few minutes.",
                    ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

private fun openBatterySettings(context: android.content.Context) {
    val flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    try {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(flags))
    } catch (_: Exception) {
        try {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(flags),
            )
        } catch (_: Exception) { /* nowhere to go */ }
    }
}

private fun openAppNotificationSettings(context: android.content.Context) {
    val flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    try {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(flags),
        )
    } catch (_: Exception) {
        try {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(flags),
            )
        } catch (_: Exception) { }
    }
}
