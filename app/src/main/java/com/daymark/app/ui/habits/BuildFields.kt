package com.daymark.app.ui.habits

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule as ScheduleIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daymark.app.domain.BuildPreset
import com.daymark.app.domain.Habit
import com.daymark.app.domain.HabitKind
import com.daymark.app.domain.ReminderConfig
import com.daymark.app.domain.ReminderMode
import com.daymark.app.domain.Schedule
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.theme.Motion
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Editable state of the build-specific part of the form (goal, schedule, reminders). */
class BuildDraft(
    tracksAmount: Boolean = false,
    target: String = "",
    unit: String = "",
    step: String = "1",
    days: Int = Schedule.EVERY_DAY,
    mode: ReminderMode = ReminderMode.NONE,
    interval: Int = 60,
    activeStart: Int = 8 * 60,
    activeEnd: Int = 21 * 60,
    times: List<Int> = emptyList(),
) {
    var tracksAmount by mutableStateOf(tracksAmount)
    var target by mutableStateOf(target)
    var unit by mutableStateOf(unit)
    var step by mutableStateOf(step)
    var days by mutableStateOf(days)
    var mode by mutableStateOf(mode)
    var interval by mutableStateOf(interval)
    var activeStart by mutableStateOf(activeStart)
    var activeEnd by mutableStateOf(activeEnd)
    var times by mutableStateOf(times)

    val intervalValid: Boolean get() = mode != ReminderMode.INTERVAL || activeEnd > activeStart
    val amountValid: Boolean get() = !tracksAmount || (target.toDoubleOrNull() ?: 0.0) > 0.0
    val isValid: Boolean get() = intervalValid && amountValid && (mode != ReminderMode.TIMES || times.isNotEmpty())

    fun applyPreset(p: BuildPreset) {
        tracksAmount = p.target > 0
        target = if (p.target > 0) fmtNum(p.target) else ""
        unit = p.unit
        step = fmtNum(p.step)
        days = p.days
        applyReminder(p.reminder)
    }

    private fun applyReminder(r: ReminderConfig) {
        mode = r.mode
        interval = r.intervalMinutes
        activeStart = r.activeStart
        activeEnd = r.activeEnd
        times = r.times
    }

    fun reminder(): ReminderConfig = when (mode) {
        ReminderMode.NONE -> ReminderConfig()
        ReminderMode.INTERVAL -> ReminderConfig(ReminderMode.INTERVAL, interval, activeStart, activeEnd, emptyList())
        ReminderMode.TIMES -> ReminderConfig(ReminderMode.TIMES, interval, activeStart, activeEnd, times.distinct().sorted())
    }

    /** Applies the draft's goal, schedule and reminders to [habit], marking it as a build habit. */
    fun applyTo(habit: Habit): Habit = habit.copy(
        kind = HabitKind.BUILD,
        goalTarget = if (tracksAmount) (target.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 1e9) else 0.0,
        goalUnit = if (tracksAmount) unit.trim() else "",
        goalStep = (step.toDoubleOrNull() ?: 1.0).let { if (it > 0) it else 1.0 },
        scheduleDays = Schedule.normalize(days),
        reminder = reminder(),
        dailyLimit = 0,
    )

    companion object {
        fun from(h: Habit?): BuildDraft {
            if (h == null || !h.isBuild) return BuildDraft()
            return BuildDraft(
                tracksAmount = h.goalTarget > 0,
                target = if (h.goalTarget > 0) fmtNum(h.goalTarget) else "",
                unit = h.goalUnit,
                step = fmtNum(h.goalStep),
                days = Schedule.normalize(h.scheduleDays),
                mode = h.reminder.mode,
                interval = h.reminder.intervalMinutes,
                activeStart = h.reminder.activeStart,
                activeEnd = h.reminder.activeEnd,
                times = h.reminder.times,
            )
        }

        val Saver = listSaver<BuildDraft, Any>(
            save = {
                listOf(
                    it.tracksAmount, it.target, it.unit, it.step, it.days, it.mode.id, it.interval,
                    it.activeStart, it.activeEnd, it.times.joinToString(","),
                )
            },
            restore = {
                BuildDraft(
                    it[0] as Boolean, it[1] as String, it[2] as String, it[3] as String, it[4] as Int,
                    ReminderMode.of(it[5] as Int), it[6] as Int, it[7] as Int, it[8] as Int,
                    (it[9] as String).split(",").mapNotNull { s -> s.toIntOrNull() },
                )
            },
        )
    }
}

@Composable
fun rememberBuildDraft(existing: Habit?): BuildDraft =
    rememberSaveable(saver = BuildDraft.Saver) { BuildDraft.from(existing) }

internal fun fmtNum(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

internal fun fmtTime(minutes: Int): String =
    LocalTime.of((minutes / 60).coerceIn(0, 23), minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun fmtInterval(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${fmtNum(min / 60.0)} h"
}

/** Two big, calm cards: build vs break. */
@Composable
fun KindChoice(kind: HabitKind, onKind: (HabitKind) -> Unit) {
    val haptics = rememberHaptics()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        KindCard(Modifier.weight(1f), "✨", "Build a habit", "Do something regularly", kind == HabitKind.BUILD) {
            haptics.tick(); onKind(HabitKind.BUILD)
        }
        KindCard(Modifier.weight(1f), "🌱", "Break a habit", "Quit or cut down", kind == HabitKind.BREAK) {
            haptics.tick(); onKind(HabitKind.BREAK)
        }
    }
}

@Composable
private fun KindCard(modifier: Modifier, emoji: String, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, Motion.standard(), label = "kindBg",
    )
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, Motion.standard(), label = "kindBorder",
    )
    DaymarkCard(
        modifier = modifier
            .border(2.dp, border, RoundedCornerShape(24.dp))
            .semantics { this.selected = selected; contentDescription = "$title. $subtitle" },
        onClick = onClick, color = bg, contentPadding = 16.dp,
    ) {
        Text(emoji, fontSize = 28.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small read-only label shown when editing (kind can't change after creation). */
@Composable
fun KindLabel(kind: HabitKind) {
    val text = if (kind == HabitKind.BUILD) "✨ Build a habit" else "🌱 Break a habit"
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Goal, schedule and reminder sections for a build habit. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BuildFields(draft: BuildDraft) {
    GoalSection(draft)
    ScheduleSection(draft)
    ReminderSection(draft)
}

@Composable
private fun GoalSection(d: BuildDraft) {
    val haptics = rememberHaptics()
    Column {
        Text("Your goal", style = MaterialTheme.typography.titleMedium)
        Text("Small steps, every day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(!d.tracksAmount, { haptics.tick(); d.tracksAmount = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Just mark done") }
            SegmentedButton(d.tracksAmount, { haptics.tick(); d.tracksAmount = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Track an amount") }
        }
        AnimatedVisibility(d.tracksAmount, enter = fadeIn() + expandVertically(Motion.standard()), exit = fadeOut() + shrinkVertically(Motion.standard())) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = d.target, onValueChange = { v -> d.target = v.filter { it.isDigit() || it == '.' }.take(8) },
                        modifier = Modifier.weight(1f), singleLine = true, label = { Text("Daily target") },
                        isError = !d.amountValid, shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = d.unit, onValueChange = { d.unit = it.take(12) },
                        modifier = Modifier.weight(1f), singleLine = true, label = { Text("Unit") },
                        placeholder = { Text("e.g. L, pages") }, shape = RoundedCornerShape(16.dp),
                    )
                }
                OutlinedTextField(
                    value = d.step, onValueChange = { v -> d.step = v.filter { it.isDigit() || it == '.' }.take(8) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Quick-add step") },
                    supportingText = { Text("What the + button adds each tap.") }, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSection(d: BuildDraft) {
    val haptics = rememberHaptics()
    val weekdays = remember { Schedule.of(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    var custom by rememberSaveable { mutableStateOf(d.days != Schedule.EVERY_DAY && d.days != weekdays) }
    // A preset may change the mask from outside; keep the chips visible if it isn't one of the two simple choices.
    val showCustom = custom || (d.days != Schedule.EVERY_DAY && d.days != weekdays)
    Column {
        Text("Which days?", style = MaterialTheme.typography.titleMedium)
        Text(Schedule.label(d.days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(!showCustom && d.days == Schedule.EVERY_DAY, { haptics.tick(); custom = false; d.days = Schedule.EVERY_DAY }, SegmentedButtonDefaults.itemShape(0, 3)) { Text("Every day", maxLines = 1) }
            SegmentedButton(!showCustom && d.days == weekdays, { haptics.tick(); custom = false; d.days = weekdays }, SegmentedButtonDefaults.itemShape(1, 3)) { Text("Weekdays", maxLines = 1) }
            SegmentedButton(showCustom, { haptics.tick(); custom = true }, SegmentedButtonDefaults.itemShape(2, 3)) { Text("Custom", maxLines = 1) }
        }
        AnimatedVisibility(showCustom, enter = fadeIn() + expandVertically(Motion.standard()), exit = fadeOut() + shrinkVertically(Motion.standard())) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val on = Schedule.isScheduled(d.days, day)
                    DayChip(Modifier.weight(1f), day, on) {
                        val next = if (on) d.days and (1 shl (day.value - 1)).inv() else d.days or (1 shl (day.value - 1))
                        // At least one day must stay selected.
                        if (next != 0) { haptics.tick(); d.days = next }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(modifier: Modifier, day: DayOfWeek, on: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, Motion.standard(), label = "dayBg")
    val fg by animateColorAsState(if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, Motion.standard(), label = "dayFg")
    val full = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Box(
        modifier
            .height(48.dp)
            .semantics { contentDescription = full; selected = on }
            .pressScale(src, 0.9f)
            .clickable(src, null, role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(38.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = fg, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderSection(d: BuildDraft) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    fun setMode(m: ReminderMode) {
        haptics.tick()
        val wasOff = d.mode == ReminderMode.NONE
        d.mode = m
        if (m == ReminderMode.TIMES && d.times.isEmpty()) d.times = listOf(9 * 60)
        if (wasOff && m != ReminderMode.NONE) askPermissionIfNeeded()
    }
    // null = no dialog; -1 = interval start; -2 = interval end; >= 0 = index in times
    var pick by remember { mutableStateOf<Int?>(null) }

    Column {
        Text("Reminders", style = MaterialTheme.typography.titleMedium)
        Text("A gentle nudge, only when you want one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(d.mode == ReminderMode.NONE, { setMode(ReminderMode.NONE) }, SegmentedButtonDefaults.itemShape(0, 3)) { Text("Off", maxLines = 1) }
            SegmentedButton(d.mode == ReminderMode.INTERVAL, { setMode(ReminderMode.INTERVAL) }, SegmentedButtonDefaults.itemShape(1, 3)) { Text("Repeating", maxLines = 1) }
            SegmentedButton(d.mode == ReminderMode.TIMES, { setMode(ReminderMode.TIMES) }, SegmentedButtonDefaults.itemShape(2, 3)) { Text("Set times", maxLines = 1) }
        }

        AnimatedVisibility(d.mode == ReminderMode.INTERVAL, enter = fadeIn() + expandVertically(Motion.standard()), exit = fadeOut() + shrinkVertically(Motion.standard())) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Remind me every", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReminderConfig.INTERVAL_CHOICES.forEach { m ->
                        FilterChip(selected = d.interval == m, onClick = { haptics.tick(); d.interval = m }, label = { Text(fmtInterval(m)) }, shape = RoundedCornerShape(12.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeCard(Modifier.weight(1f), "From", d.activeStart) { pick = -1 }
                    TimeCard(Modifier.weight(1f), "To", d.activeEnd) { pick = -2 }
                }
                if (!d.intervalValid) {
                    Text("Pick an end time after the start time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        AnimatedVisibility(d.mode == ReminderMode.TIMES, enter = fadeIn() + expandVertically(Motion.standard()), exit = fadeOut() + shrinkVertically(Motion.standard())) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.times.forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimeCard(Modifier.weight(1f), "At", t) { pick = i }
                        IconButton(onClick = { d.times = d.times.filterIndexed { j, _ -> j != i } }, enabled = d.times.size > 1) {
                            Icon(Icons.Rounded.Close, "Remove reminder at ${fmtTime(t)}")
                        }
                    }
                }
                if (d.times.size < 6) {
                    TextButton(onClick = {
                        haptics.tick()
                        var next = ((d.times.maxOrNull() ?: (8 * 60)) + 120).coerceAtMost(23 * 60)
                        while (next in d.times && next > 0) next -= 30
                        d.times = d.times + next
                    }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add a time")
                    }
                }
            }
        }

        val preview = when {
            d.mode == ReminderMode.INTERVAL && d.intervalValid ->
                "About ${(d.activeEnd - d.activeStart) / d.interval + 1} reminders between ${fmtTime(d.activeStart)} and ${fmtTime(d.activeEnd)}"
            d.mode == ReminderMode.TIMES -> "Reminders at " + d.times.distinct().sorted().joinToString(", ") { fmtTime(it) }
            d.mode == ReminderMode.NONE -> "No reminders. You can still check in any time."
            else -> null
        }
        if (preview != null) {
            Spacer(Modifier.height(10.dp))
            Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    pick?.let { code ->
        val initial = when (code) {
            -1 -> d.activeStart
            -2 -> d.activeEnd
            else -> d.times.getOrNull(code) ?: (9 * 60)
        }
        TimeDialog(initial, onDismiss = { pick = null }) { minutes ->
            when (code) {
                -1 -> d.activeStart = minutes
                -2 -> d.activeEnd = minutes
                else -> d.times = d.times.mapIndexed { j, t -> if (j == code) minutes else t }
            }
            pick = null
        }
    }
}

@Composable
private fun TimeCard(modifier: Modifier, label: String, minutes: Int, onClick: () -> Unit) {
    DaymarkCard(modifier.semantics { contentDescription = "$label ${fmtTime(minutes)}. Tap to change" }, onClick = onClick, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ScheduleIcon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(fmtTime(minutes), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val state = rememberTimePickerState(initialMinutes / 60, initialMinutes % 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onDone(state.hour * 60 + state.minute) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
