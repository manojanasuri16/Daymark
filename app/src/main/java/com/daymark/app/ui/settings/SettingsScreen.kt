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
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VisibilityOff
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

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
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

        item { SectionHeader("Home screen") }
        item {
            Group {
                SettingRow(Icons.Rounded.Widgets, "Widgets", "Streak, progress ring, heatmap and daily motivation — five styles", onClick = onOpenWidgets)
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
                    exportLauncher.launch("daymark-backup-${LocalDate.now()}.json")
                })
                Divider()
                SettingRow(Icons.Rounded.Restore, "Restore from backup", "Replace current data with a backup file", onClick = {
                    confirmRestore = true
                })
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
private fun Group(content: @Composable () -> Unit) {
    DaymarkCard(contentPadding = 0.dp) { content() }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SettingRow(
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
private fun SwitchRow(
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
