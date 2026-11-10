package com.daymark.app.ui.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daymark.app.domain.Habit
import com.daymark.app.domain.HabitKind
import com.daymark.app.domain.Options
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.theme.Motion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Name, symbol and start time of a tracker. Used for onboarding (no top bar), adding and editing.
 * [existing] null = create.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorScreen(
    existing: Habit?,
    canDelete: Boolean,
    onSave: (Habit) -> Unit,
    onDelete: (Habit) -> Unit,
    onClose: () -> Unit,
) {
    val haptics = rememberHaptics()
    val zone = ZoneId.systemDefault()
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var emoji by rememberSaveable { mutableStateOf(existing?.emoji ?: Options.emojis.first()) }
    var startedEarlier by rememberSaveable { mutableStateOf(existing != null) }
    var start by rememberSaveable { mutableLongStateOf(existing?.startAt ?: System.currentTimeMillis()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var dailyLimit by rememberSaveable { mutableStateOf(existing?.dailyLimit ?: 0) }
    var spend by rememberSaveable { mutableStateOf(existing?.spendAmount?.takeIf { it > 0 }?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var spendPeriod by rememberSaveable { mutableStateOf(existing?.spendPeriodDays ?: 7) }
    // Newline-joined so it survives process death.
    var reasons by rememberSaveable { mutableStateOf(existing?.reasons.orEmpty().joinToString("\n")) }
    var kindId by rememberSaveable { mutableStateOf(existing?.kind?.id ?: HabitKind.BREAK.id) }
    val kind = HabitKind.of(kindId)
    val build = rememberBuildDraft(existing)
    val isBuild = kind == HabitKind.BUILD
    val canSave = if (isBuild) name.isNotBlank() && build.isValid else true

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (existing == null) (if (isBuild) "New habit" else "New tracker") else if (isBuild) "Edit habit" else "Edit tracker") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
            actions = {
                if (existing != null && canDelete) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete tracker") }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (existing == null) {
                KindChoice(kind) { k ->
                    if (k.id != kindId) {
                        kindId = k.id
                        // Fresh slate for the other kind so one form's text doesn't leak into the other.
                        name = ""; emoji = if (k == HabitKind.BUILD) Options.buildEmojis.first() else Options.emojis.first()
                    }
                }
            } else {
                KindLabel(kind)
            }
            if (existing == null && !isBuild) {
                Text(
                    "Track each thing you're moving away from separately — every tracker keeps its own streaks, calendar and insights.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isBuild) {
                HabitFields(
                    name = name, onName = { name = it },
                    emoji = emoji, onEmoji = { emoji = it },
                    startedEarlier = startedEarlier, onStartedEarlier = { startedEarlier = it; if (!it) start = System.currentTimeMillis() },
                    start = start, onStart = { start = it },
                    isNew = existing == null,
                    nameTitle = "What will you build?",
                    namePlaceholder = "e.g. Drink water",
                    suggestions = Options.buildPresets.map { it.name },
                    onSuggestion = { i ->
                        val p = Options.buildPresets[i]
                        name = p.name; emoji = p.emoji; build.applyPreset(p)
                    },
                    emojis = Options.buildEmojis,
                    startTitle = "When do you begin?",
                    nowLabel = "Today",
                )
                BuildFields(build)
            } else {
                HabitFields(
                    name = name, onName = { name = it },
                    emoji = emoji, onEmoji = { emoji = it },
                    startedEarlier = startedEarlier, onStartedEarlier = { startedEarlier = it; if (!it) start = System.currentTimeMillis() },
                    start = start, onStart = { start = it },
                    isNew = existing == null,
                )
                GoalFields(dailyLimit) { dailyLimit = it }
                MoneyFields(spend, { spend = it }, spendPeriod, { spendPeriod = it })
                ReasonsFields(reasons.split("\n").filter { it.isNotBlank() }) { reasons = it.joinToString("\n") }
            }
            Spacer(Modifier.height(4.dp))
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                onClick = {
                    haptics.confirm()
                    val startAt = if (existing == null && !startedEarlier) System.currentTimeMillis() else minOf(start, System.currentTimeMillis())
                    val base = (existing ?: Habit(0, "", emoji, startAt)).copy(name = name.trim(), emoji = emoji, startAt = startAt)
                    onSave(
                        if (isBuild) build.applyTo(base)
                        else base.copy(
                            kind = HabitKind.BREAK,
                            dailyLimit = dailyLimit,
                            spendAmount = spend.replace(",", "").toDoubleOrNull()?.coerceIn(0.0, 1e9) ?: 0.0,
                            spendPeriodDays = spendPeriod,
                            reasons = reasons.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (existing == null) (if (isBuild) "Start building" else "Start tracking") else "Save changes", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${existing.displayName}?") },
            text = { Text("This tracker and all of its entries will be permanently removed. Your other trackers aren't affected.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; haptics.heavy(); onDelete(existing) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

/** The shared form, also embedded in onboarding. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitFields(
    name: String, onName: (String) -> Unit,
    emoji: String, onEmoji: (String) -> Unit,
    startedEarlier: Boolean, onStartedEarlier: (Boolean) -> Unit,
    start: Long, onStart: (Long) -> Unit,
    isNew: Boolean,
    nameTitle: String = "What are you moving away from?",
    namePlaceholder: String = "Optional — e.g. Alcohol",
    suggestions: List<String> = Options.habitSuggestions,
    /** Called with the tapped suggestion's index; null = fill the name with it. */
    onSuggestion: ((Int) -> Unit)? = null,
    emojis: List<String> = Options.emojis,
    startTitle: String = "When did you start?",
    nowLabel: String = "Starting now",
) {
    val haptics = rememberHaptics()
    val zone = ZoneId.systemDefault()
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Column {
        Text(nameTitle, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name, onValueChange = { onName(it.take(40)) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text(namePlaceholder) },
            leadingIcon = { Text(emoji, fontSize = 22.sp) },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            suggestions.forEachIndexed { i, s ->
                SuggestionChip(onClick = { haptics.tick(); if (onSuggestion != null) onSuggestion(i) else onName(s) }, label = { Text(s) }, shape = RoundedCornerShape(12.dp))
            }
        }
    }

    Column {
        Text("Choose a symbol", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            emojis.forEach { e -> EmojiChoice(e, e == emoji) { haptics.tick(); onEmoji(e) } }
        }
    }

    Column {
        Text(if (isNew) startTitle else "Tracking since", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        if (isNew) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!startedEarlier, { onStartedEarlier(false) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(nowLabel) }
                SegmentedButton(startedEarlier, { onStartedEarlier(true) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Earlier") }
            }
        }
        AnimatedVisibility(startedEarlier || !isNew, enter = fadeIn() + expandVertically(Motion.standard()), exit = fadeOut() + shrinkVertically(Motion.standard())) {
            val dt = Instant.ofEpochMilli(start).atZone(zone)
            Row(Modifier.padding(top = if (isNew) 12.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DaymarkCard(Modifier.weight(1.3f), onClick = { showDate = true }, contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(dt.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = MaterialTheme.typography.titleSmall)
                    }
                }
                DaymarkCard(Modifier.weight(1f), onClick = { showTime = true }, contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(dt.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }

    if (showDate) {
        val today = LocalDate.now()
        val dt = Instant.ofEpochMilli(start).atZone(zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        onStart(minOf(LocalDateTime.of(d, dt.toLocalTime()).atZone(zone).toInstant().toEpochMilli(), System.currentTimeMillis()))
                    }
                    showDate = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (showTime) {
        val dt = Instant.ofEpochMilli(start).atZone(zone)
        val state = rememberTimePickerState(dt.hour, dt.minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    onStart(minOf(LocalDateTime.of(dt.toLocalDate(), LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli(), System.currentTimeMillis()))
                    showTime = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmojiChoice(emoji: String, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, Motion.bouncy(0.35f), label = "emoji")
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, Motion.standard(), label = "emojiBg",
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.size(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }.pressScale(src, 0.9f).clickable(src, null, onClick = onClick),
    ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) } }
}

@Composable
private fun GoalFields(dailyLimit: Int, onChange: (Int) -> Unit) {
    val haptics = rememberHaptics()
    Column {
        Text("Your goal", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(dailyLimit == 0, { haptics.tick(); onChange(0) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Quit completely") }
            SegmentedButton(dailyLimit > 0, { haptics.tick(); if (dailyLimit == 0) onChange(1) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Cut down") }
        }
        AnimatedVisibility(dailyLimit > 0, enter = fadeIn() + expandVertically(Motion.standard()), exit = fadeOut() + shrinkVertically(Motion.standard())) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Up to ${dailyLimit.coerceAtLeast(1)} a day", style = MaterialTheme.typography.titleSmall)
                    Text("A day only counts as a slip when you go over.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { haptics.tick(); onChange((dailyLimit - 1).coerceAtLeast(1)) }, enabled = dailyLimit > 1) {
                    Icon(Icons.Rounded.Remove, "Fewer")
                }
                IconButton(onClick = { haptics.tick(); onChange((dailyLimit + 1).coerceAtMost(20)) }) { Icon(Icons.Rounded.Add, "More") }
            }
        }
    }
}

@Composable
private fun MoneyFields(amount: String, onAmount: (String) -> Unit, periodDays: Int, onPeriod: (Int) -> Unit) {
    Column {
        Text("What did it cost you?", style = MaterialTheme.typography.titleMedium)
        Text("Optional. Daymark shows the money you keep.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> onAmount(v.filter { it.isDigit() || it == '.' }.take(10)) },
                modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("0") },
                prefix = { Text(com.daymark.app.domain.Format.currencySymbol()) },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.width(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.weight(1.1f)) {
                SegmentedButton(periodDays == 7, { onPeriod(7) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("/ week") }
                SegmentedButton(periodDays == 30, { onPeriod(30) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("/ month") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonsFields(reasons: List<String>, onChange: (List<String>) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val add = {
        val t = draft.replace("\n", " ").trim().take(120)
        if (t.isNotEmpty() && reasons.size < 12) onChange(reasons + t)
        draft = ""
    }
    Column {
        Text("Your reasons", style = MaterialTheme.typography.titleMedium)
        Text("Why this matters to you. Shown on hard days and when an urge hits.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        reasons.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(r, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onChange(reasons.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Rounded.Close, "Remove reason", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        OutlinedTextField(
            value = draft, onValueChange = { draft = it.take(120) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("e.g. I want to be present for my family") },
            shape = RoundedCornerShape(16.dp),
            trailingIcon = { IconButton(onClick = add, enabled = draft.isNotBlank()) { Icon(Icons.Rounded.Add, "Add reason") } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
    }
}
