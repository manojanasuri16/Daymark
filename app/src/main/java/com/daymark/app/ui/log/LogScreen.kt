package com.daymark.app.ui.log

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.Format
import com.daymark.app.domain.Messages
import com.daymark.app.domain.NonSlipNote
import com.daymark.app.domain.Options
import com.daymark.app.domain.RelapseRecord
import com.daymark.app.domain.StreakEngine
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.theme.Motion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Create or edit a relapse entry.
 * @param editId existing entry id, or 0 to create.
 * @param initialDate epoch-day to prefill (from the calendar), or null for now.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogScreen(
    vm: MainViewModel,
    editId: Long,
    initialDate: Long?,
    onClose: () -> Unit,
    onDeleted: (RelapseRecord) -> Unit,
    noteId: Long = 0,
) {
    val zone = ZoneId.systemDefault()
    val haptics = rememberHaptics()
    val habit by vm.selectedHabit.collectAsStateWithLifecycle()

    var loaded by rememberSaveable { mutableStateOf(editId == 0L && noteId == 0L) }
    // 0 = slip, 1 = "not a slip" note. Only chosen when creating a new entry.
    var kind by rememberSaveable { mutableStateOf(0) }
    val isNewEntry = editId == 0L && noteId == 0L
    val noteMode = noteId != 0L || (isNewEntry && kind == 1)
    var originalNote by remember { mutableStateOf<NonSlipNote?>(null) }
    var timestamp by rememberSaveable {
        mutableLongStateOf(
            initialDate?.let { day ->
                val date = LocalDate.ofEpochDay(day)
                val t = if (date == LocalDate.now()) LocalTime.now() else LocalTime.of(20, 0)
                date.atTime(t).atZone(zone).toInstant().toEpochMilli()
            } ?: System.currentTimeMillis(),
        )
    }
    var mood by rememberSaveable { mutableStateOf(0) }
    var situation by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val triggers = remember { mutableStateListOf<String>() }
    val customTriggers = remember { mutableStateListOf<String>() }
    var original by remember { mutableStateOf<RelapseRecord?>(null) }

    LaunchedEffect(editId) {
        if (editId != 0L && original == null) {
            val r = vm.allRelapses.value.firstOrNull { it.id == editId }
                ?: withTimeoutOrNull(3_000) { vm.allRelapses.first { l -> l.any { it.id == editId } } }?.firstOrNull { it.id == editId }
            if (r != null) {
                original = r
                if (!loaded) {
                    timestamp = r.timestamp; mood = r.mood; situation = r.situation; notes = r.notes
                    triggers.clear(); triggers.addAll(r.triggers)
                    customTriggers.clear(); customTriggers.addAll(r.triggers.filter { it !in Options.triggers })
                    loaded = true
                }
            }
        }
    }

    LaunchedEffect(noteId) {
        if (noteId != 0L && originalNote == null) {
            val n = vm.notes.value.firstOrNull { it.id == noteId }
                ?: withTimeoutOrNull(3_000) { vm.notes.first { l -> l.any { it.id == noteId } } }?.firstOrNull { it.id == noteId }
            if (n != null) {
                originalNote = n
                if (!loaded) {
                    timestamp = n.timestamp; notes = n.notes
                    loaded = true
                }
            }
        }
    }

    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var showConfirmDelete by remember { mutableStateOf(false) }
    var savedSummary by remember { mutableStateOf<String?>(null) }

    val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
    val isFuture = timestamp > System.currentTimeMillis() + 60_000

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(if (noteId != 0L) "Edit note" else if (noteMode) "Not a slip" else if (editId != 0L) "Edit entry" else if (habit?.isReduce == true) "Log a use" else "Log a slip")
                    habit?.let {
                        Text("${it.emoji}  ${it.displayName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
            actions = {
                if (editId != 0L || noteId != 0L) IconButton(onClick = { showConfirmDelete = true }) {
                    Icon(Icons.Rounded.DeleteOutline, if (noteId != 0L) "Delete note" else "Delete entry")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (isNewEntry) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KindOption("Slip", "Resets your current streak", kind == 0, Modifier.weight(1f)) { haptics.tick(); kind = 0 }
                    KindOption("Not a slip", "Saved as a note. Your streak stays safe", kind == 1, Modifier.weight(1f)) { haptics.tick(); kind = 1 }
                }
                Text(
                    if (noteMode) "Happens to everyone. This is just a note, your streak is safe."
                    else "Being honest with yourself is part of recovery. This stays private, on this device only.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("When") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PickerField(
                        icon = Icons.Rounded.Event,
                        text = dateTime.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        modifier = Modifier.weight(1.3f),
                        onClick = { showDate = true },
                    )
                    PickerField(
                        icon = Icons.Rounded.Schedule,
                        text = dateTime.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                        modifier = Modifier.weight(1f),
                        onClick = { showTime = true },
                    )
                }
                if (isFuture) {
                    Text("That time is in the future.", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                }
            }

            if (!noteMode) Section("How were you feeling?") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Options.moods.forEach { m ->
                        MoodButton(m.emoji, m.label, selected = mood == m.value) {
                            haptics.tick(); mood = if (mood == m.value) 0 else m.value
                        }
                    }
                }
            }

            if (!noteMode) Section("What triggered it?", hint = "Choose any that apply") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Options.triggers + customTriggers).distinct().forEach { t ->
                        val on = t in triggers
                        FilterChip(
                            selected = on,
                            onClick = { haptics.tick(); if (on) triggers.remove(t) else triggers.add(t) },
                            label = { Text(t) },
                            leadingIcon = if (on) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { showCustom = true },
                        label = { Text("Other") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            if (!noteMode) Section("Where were you?") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Options.situations.forEach { s ->
                        FilterChip(
                            selected = situation == s,
                            onClick = { haptics.tick(); situation = if (situation == s) "" else s },
                            label = { Text(s) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }

            Section("Notes", hint = if (noteMode) null else "What happened, and what might help next time?") {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(2000) },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    placeholder = { Text(if (noteMode) "Optional. Write whatever fits." else "Optional") },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                onClick = {
                    haptics.confirm()
                    if (noteMode) {
                        vm.saveNote(
                            NonSlipNote(
                                id = noteId,
                                habitId = originalNote?.habitId ?: habit?.id ?: 1L,
                                timestamp = timestamp,
                                notes = notes.trim(),
                            ),
                        )
                        onClose()
                        return@Button
                    }
                    val record = RelapseRecord(
                        id = if (editId == 0L) 0 else editId,
                        timestamp = timestamp,
                        triggers = triggers.toList(),
                        mood = mood,
                        situation = situation,
                        notes = notes.trim(),
                        habitId = original?.habitId ?: habit?.id ?: 1L,
                    )
                    vm.saveRelapse(record)
                    if (editId != 0L) onClose()
                    else {
                        val raw = vm.relapses.value.map { it.timestamp } + timestamp
                        val ts = habit?.slipTimes(raw) ?: raw
                        val stats = StreakEngine.compute(habit?.startAt ?: timestamp, ts, System.currentTimeMillis())
                        savedSummary = if (stats.longestDays == 0L && stats.totalCleanDays == 0) {
                            "Every honest entry helps you understand your patterns. Your next streak starts right now."
                        } else {
                            "Your best streak of ${Format.days(stats.longestDays)} and all " +
                                "${Format.days(stats.totalCleanDays.toLong())} you've stayed clean are still yours."
                        }
                    }
                },
                enabled = loaded && !isFuture,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (noteMode) (if (noteId == 0L) "Save note" else "Save changes") else if (editId == 0L) "Save entry" else "Save changes", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (showDate) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
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
                        timestamp = LocalDateTime.of(d, dateTime.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (showTime) {
        val state = rememberTimePickerState(dateTime.hour, dateTime.minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    timestamp = LocalDateTime.of(dateTime.toLocalDate(), LocalTime.of(state.hour, state.minute))
                        .atZone(zone).toInstant().toEpochMilli()
                    showTime = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
        )
    }

    if (showCustom) {
        var text by remember { mutableStateOf("") }
        val add = {
            val t = text.trim().replace("|", "/").take(30)
            if (t.isNotEmpty()) {
                if (t !in Options.triggers && t !in customTriggers) customTriggers.add(t)
                if (t !in triggers) triggers.add(t)
            }
            showCustom = false
        }
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("Add a trigger") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    placeholder = { Text("e.g. Payday") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
            },
            confirmButton = { TextButton(onClick = add) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Cancel") } },
        )
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text(if (noteId != 0L) "Delete this note?" else "Delete this entry?") },
            text = { Text(if (noteId != 0L) "Your streaks are not affected." else "Your streaks will be recalculated. You can undo this right after.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDelete = false
                    if (noteId != 0L) {
                        originalNote?.let { vm.deleteNote(it) }
                        onClose()
                    } else {
                        original?.let { vm.deleteRelapse(it); onDeleted(it) } ?: onClose()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showConfirmDelete = false }) { Text("Keep") } },
        )
    }

    savedSummary?.let { summary ->
        val message = remember { Messages.afterRelapse.random() }
        AlertDialog(
            onDismissRequest = { savedSummary = null; onClose() },
            icon = { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(message, textAlign = TextAlign.Center) },
            text = { Text(summary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = { savedSummary = null; onClose() }, shape = RoundedCornerShape(14.dp)) { Text("Begin again") }
            },
        )
    }
}

@Composable
private fun KindOption(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
internal fun PickerField(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    DaymarkCard(modifier = modifier, onClick = onClick, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

@Composable
internal fun MoodButton(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, Motion.bouncy(0.35f), label = "mood")
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        Motion.standard(), label = "moodBg",
    )
    Column(
        Modifier.pressScale(source, 0.92f).clickable(source, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = bg,
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.size(58.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 28.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
