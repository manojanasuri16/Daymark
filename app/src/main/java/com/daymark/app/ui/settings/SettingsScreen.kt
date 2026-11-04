package com.daymark.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
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
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
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

    var editName by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<LocalDate?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val start = Instant.ofEpochMilli(settings.journeyStart).atZone(zone)

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

        item { SectionHeader("Your journey") }
        item {
            Group {
                SettingRow(Icons.Rounded.Edit, "Moving away from", settings.habitName.ifBlank { "Not set" }, onClick = { editName = true })
                Divider()
                SettingRow(
                    Icons.Rounded.Event, "Journey start",
                    start.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)),
                    onClick = { pickDate = true },
                )
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Divider()
                    SwitchRow(Icons.Rounded.Palette, "Wallpaper colours", "Use Material You dynamic colour", settings.dynamicColor) {
                        vm.setDynamicColor(it)
                    }
                }
                Divider()
                SwitchRow(Icons.Rounded.Vibration, "Haptic feedback", "Gentle taps for meaningful moments", settings.haptics) {
                    vm.setHaptics(it)
                }
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

    if (editName) {
        var text by remember { mutableStateOf(settings.habitName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("What are you moving away from?") },
            text = {
                OutlinedTextField(text, { text = it.take(40) }, singleLine = true, placeholder = { Text("e.g. Alcohol") },
                    shape = RoundedCornerShape(14.dp))
            },
            confirmButton = { TextButton(onClick = { vm.setHabitName(text); editName = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } },
        )
    }

    if (pickDate) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDate = false
                    pickTime = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    pickTime?.let { date ->
        val state = rememberTimePickerState(start.hour, start.minute)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            title = { Text("Start time") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalDateTime.of(date, LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli()
                    vm.setJourneyStart(minOf(t, System.currentTimeMillis()))
                    pickTime = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("Cancel") } },
        )
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
