package com.daymark.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.daymark.app.domain.Habit
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.theme.Accent
import com.daymark.app.ui.theme.DaymarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.BuildConfigInfo
import com.daymark.app.data.Settings
import com.daymark.app.data.ThemeMode
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.SectionHeader
import com.daymark.app.ui.components.rememberHaptics
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onOpenWidgets: () -> Unit = {},
    onOpenEventNotifications: () -> Unit = {},
    onOpenEventWidgets: () -> Unit = {},
    onImportCalendar: () -> Unit = {},
    onAddHabit: () -> Unit = {},
    onEditHabit: (Habit) -> Unit = {},
) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val settings = s ?: Settings()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val zone = ZoneId.systemDefault()

    val canLock = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var exportPassword by remember { mutableStateOf("") }
    var askExportPassword by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri, exportPassword)
        exportPassword = ""
    }
    val pendingImport by vm.pendingEncryptedImport.collectAsStateWithLifecycle()

    var pickedFolder by remember { mutableStateOf<android.net.Uri?>(null) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) pickedFolder = uri
    }
    var confirmAutoOff by remember { mutableStateOf(false) }

    // Notification permission (Android 13+). The toggle that asked is applied once granted.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) afterPermission?.invoke()
        afterPermission = null
    }
    fun withNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            afterPermission = action
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }
    var showCheckInTime by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    var confirmRestore by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }


    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(4.dp)) }

        item { SectionHeader("Trackers") }
        item {
            Group {
                habits.orEmpty().forEachIndexed { i, h ->
                    if (i > 0) Divider()
                    val since = Instant.ofEpochMilli(h.startAt).atZone(zone).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    Row(
                        Modifier.fillMaxWidth().clickable { onEditHabit(h) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(h.emoji, fontSize = 22.sp, modifier = Modifier.width(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text("Tracking since $since", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                Divider()
                SettingRow(Icons.Rounded.Add, "Add a tracker", "Track another habit with its own streaks", onClick = onAddHabit)
            }
        }

        item { SectionHeader("Appearance") }
        item {
            Group {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { haptics.tick(); vm.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(label) }
                        }
                    }
                }
                Divider()
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (settings.dynamicColor) "Using wallpaper colours" else Accent.of(settings.accent).label,
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(start = 40.dp)) {
                        items(Accent.entries) { a ->
                            AccentDot(a, selected = !settings.dynamicColor && a.id == Accent.of(settings.accent).id) {
                                haptics.tick()
                                if (settings.dynamicColor) vm.setDynamicColor(false)
                                vm.setAccent(a.id)
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Divider()
                    SwitchRow(Icons.Rounded.Wallpaper, "Wallpaper colours", "Use Material You dynamic colour", settings.dynamicColor) {
                        vm.setDynamicColor(it)
                    }
                }
                Divider()
                SwitchRow(Icons.Rounded.Vibration, "Haptic feedback", "Gentle taps for meaningful moments", settings.haptics) {
                    vm.setHaptics(it)
                }
            }
        }

        item { SectionHeader("Reminders") }
        item {
            Group {
                val time = LocalTime.of(settings.checkInMinutes / 60, settings.checkInMinutes % 60)
                    .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                SwitchRow(Icons.Rounded.NotificationsActive, "Daily check-in", if (settings.checkInReminder) "Every day at $time" else "A gentle nudge to reflect", settings.checkInReminder) { on ->
                    if (on) withNotificationPermission { vm.setCheckInReminder(true, settings.checkInMinutes) }
                    else vm.setCheckInReminder(false, settings.checkInMinutes)
                }
                if (settings.checkInReminder) {
                    Divider()
                    SettingRow(Icons.Rounded.Schedule, "Reminder time", time, onClick = { showCheckInTime = true })
                }
                Divider()
                SwitchRow(Icons.Rounded.Celebration, "Milestone celebrations", "A notification when you reach a new milestone", settings.milestoneAlerts) { on ->
                    if (on) withNotificationPermission { vm.setMilestoneAlerts(true) } else vm.setMilestoneAlerts(false)
                }
            }
        }

        item { SectionHeader("Events") }
        item {
            Group {
                val on = listOf(
                    settings.eventCompleted, settings.eventOneDayBefore, settings.eventOneWeekBefore,
                    settings.eventEveryDayAfter, settings.eventEveryWeekAfter, settings.eventEveryMonthAfter, settings.eventEveryYearAfter,
                ).count { it }
                SettingRow(
                    Icons.Rounded.NotificationsActive, "Notifications",
                    if (on == 0) "Off · countdown and time-since alerts" else "$on switched on",
                    onClick = onOpenEventNotifications,
                )
                Divider()
                SettingRow(
                    Icons.Rounded.Widgets, "Widgets",
                    "Refresh every ${settings.widgetRefreshMinutes} min · battery and troubleshooting",
                    onClick = onOpenEventWidgets,
                )
                Divider()
                SettingRow(
                    Icons.Rounded.CalendarMonth, "Import from calendar",
                    "Pick upcoming calendar events to count down to",
                    onClick = onImportCalendar,
                )
            }
        }

        item { SectionHeader("Home screen & quick access") }
        item {
            Group {
                SettingRow(Icons.Rounded.Widgets, "Widgets", "Streak, progress ring, heatmap and daily motivation — five styles", onClick = onOpenWidgets)
                Divider()
                SettingRow(
                    Icons.Rounded.Shield, "Quick Settings tile",
                    if (Build.VERSION.SDK_INT >= 33) "Add a \"Daymark\" tile to ride out an urge in one tap"
                    else "Edit your Quick Settings panel and drag in the \"Daymark\" tile",
                    onClick = if (Build.VERSION.SDK_INT >= 33) ({ requestTile(context) }) else null,
                )
                Divider()
                SettingRow(Icons.Rounded.TouchApp, "App shortcuts", "Long-press the Daymark icon: urge, journal or log an entry", onClick = null)
            }
        }

        item { SectionHeader("Privacy") }
        item {
            Group {
                SwitchRow(
                    Icons.Rounded.Fingerprint, "App lock",
                    if (canLock) "Require fingerprint, face or screen lock" else "Set up a screen lock on your device first",
                    settings.appLock && canLock, enabled = canLock,
                ) { vm.setAppLock(it) }
                Divider()
                SwitchRow(Icons.Rounded.VisibilityOff, "Hide in recent apps", "Blank the app preview and block screenshots", settings.hideInRecents) {
                    vm.setHideInRecents(it)
                }
                Divider()
                SwitchRow(
                    Icons.Rounded.Masks, "Discreet mode",
                    "Widgets, notifications and share cards never name what you're tracking",
                    settings.discreet,
                ) { vm.setDiscreet(it) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Shield, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Daymark has no accounts, no analytics and no internet access. Everything stays on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SectionHeader("Backup") }
        item {
            Group {
                SettingRow(Icons.Rounded.Backup, "Export backup", "Save your history as a file you control", onClick = {
                    askExportPassword = true
                })
                Divider()
                SettingRow(Icons.Rounded.Restore, "Restore from backup", "Replace current data with a backup file", onClick = {
                    confirmRestore = true
                })
                Divider()
                SwitchRow(
                    Icons.Rounded.Photo, "Include journal photos",
                    "Backups get bigger with photos. Turn off to back up text only.",
                    settings.backupPhotos,
                ) { vm.setBackupPhotos(it) }
                Divider()
                SettingRow(
                    Icons.Rounded.FolderOpen, "Automatic backups",
                    if (settings.autoBackupUri.isEmpty()) "Off · save a daily copy to a folder you choose"
                    else "On · daily, keeps the last 7" + if (settings.autoBackupPassword.isNotEmpty()) " · password-protected" else "",
                    onClick = { if (settings.autoBackupUri.isEmpty()) folderLauncher.launch(null) else confirmAutoOff = true },
                )
            }
        }

        item { SectionHeader("Data") }
        item {
            Group {
                SettingRow(Icons.Rounded.DeleteForever, "Erase all data", "Start over completely", destructive = true, onClick = { confirmReset = true })
            }
        }
        item {
            Group {
                SettingRow(Icons.Rounded.Info, "Daymark ${BuildConfigInfo.versionName}", "Made with care. One day at a time.", onClick = null)
                Divider()
                SettingRow(
                    Icons.Rounded.Info, "Font credits",
                    "Event cards use Inter, Quicksand, Roboto Condensed, Roboto Mono, Roboto Flex, Libre Baskerville, Gloock and Merienda (SIL Open Font License 1.1), plus Roboto Slab and Permanent Marker (Apache License 2.0).",
                    onClick = null,
                )
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore a backup?") },
            text = { Text("Your current history on this device will be replaced by the backup's contents.") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text("Choose file")
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }

    if (askExportPassword) {
        PasswordDialog(
            title = "Protect this backup?",
            body = "Add a password to encrypt the file, or leave it blank for a plain backup. There's no way to recover a forgotten password.",
            confirm = "Continue",
            allowBlank = true,
            onDismiss = { askExportPassword = false },
        ) { pw ->
            askExportPassword = false
            exportPassword = pw
            exportLauncher.launch("daymark-backup-${LocalDate.now()}.json")
        }
    }

    pendingImport?.let { uri ->
        PasswordDialog(
            title = "Password-protected backup",
            body = "Enter the password you used when you made this backup.",
            confirm = "Restore",
            allowBlank = false,
            onDismiss = { vm.cancelEncryptedImport() },
        ) { pw -> vm.importBackup(uri, pw) }
    }

    pickedFolder?.let { folder ->
        PasswordDialog(
            title = "Automatic backups",
            body = "Daymark will save a copy here every day and keep the last 7. Add a password to encrypt them (recommended if the folder syncs to a cloud), or leave blank.",
            confirm = "Turn on",
            allowBlank = true,
            onDismiss = { pickedFolder = null },
        ) { pw -> pickedFolder = null; vm.setAutoBackup(folder, pw) }
    }

    if (confirmAutoOff) {
        AlertDialog(
            onDismissRequest = { confirmAutoOff = false },
            title = { Text("Turn off automatic backups?") },
            text = { Text("Backups already saved in the folder are kept.") },
            confirmButton = { TextButton(onClick = { confirmAutoOff = false; vm.setAutoBackup(null, "") }) { Text("Turn off") } },
            dismissButton = { TextButton(onClick = { confirmAutoOff = false }) { Text("Cancel") } },
        )
    }

    if (showCheckInTime) {
        val state = rememberTimePickerState(settings.checkInMinutes / 60, settings.checkInMinutes % 60)
        AlertDialog(
            onDismissRequest = { showCheckInTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = { showCheckInTime = false; vm.setCheckInReminder(true, state.hour * 60 + state.minute) }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showCheckInTime = false }) { Text("Cancel") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Erase everything?") },
            text = { Text("All entries, streaks and settings will be permanently deleted. Consider exporting a backup first.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; haptics.heavy(); vm.resetEverything() }) {
                    Text("Erase", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccentDot(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val dark = DaymarkTheme.colors.isDark
    val c = Color(if (dark) accent.dark else accent.light)
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(44.dp)
            .pressScale(src, 0.88f)
            .clip(CircleShape)
            .clickable(src, null, onClick = onClick)
            .then(if (selected) Modifier.border(3.dp, c, CircleShape) else Modifier)
            .padding(if (selected) 6.dp else 2.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(accent.heroLight.first), c))),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, accent.label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun Group(content: @Composable () -> Unit) {
    DaymarkCard(contentPadding = 0.dp) { content() }
}

@Composable
internal fun Divider() = HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled) { haptics.tick(); onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = { haptics.tick(); onChange(it) }, enabled = enabled)
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    body: String,
    confirm: String,
    allowBlank: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    val needsRepeat = allowBlank && pw.isNotEmpty()
    val ok = (allowBlank || pw.isNotEmpty()) && (!needsRepeat || pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body)
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it.take(128) }, singleLine = true,
                    label = { Text(if (allowBlank) "Password (optional)" else "Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                )
                if (needsRepeat) {
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it.take(128) }, singleLine = true,
                        label = { Text("Repeat password") },
                        isError = pw2.isNotEmpty() && pw2 != pw,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }, enabled = ok) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Android 13+: ask the system to add the Daymark tile to Quick Settings. */
private fun requestTile(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < 33) return
    val sbm = context.getSystemService(android.app.StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        android.content.ComponentName(context, com.daymark.app.notify.UrgeTileService::class.java),
        "Daymark",
        android.graphics.drawable.Icon.createWithResource(context, com.daymark.app.R.drawable.ic_notification),
        context.mainExecutor,
    ) { }
}
