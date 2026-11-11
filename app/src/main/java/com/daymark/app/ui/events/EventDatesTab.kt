package com.daymark.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventEditing
import com.daymark.app.domain.EventEngine
import com.daymark.app.domain.EventKind
import com.daymark.app.domain.EventRepeat
import com.daymark.app.domain.DurationField
import com.daymark.app.ui.components.DaymarkCard
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val zone: ZoneId get() = ZoneId.systemDefault()
private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
private fun localTime(ms: Long): LocalTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().withSecond(0).withNano(0)
internal fun fmtDate(ms: Long): String = dateFmt.format(localDate(ms))
internal fun fmtTime(ms: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(localTime(ms))
private fun fmtDateTime(ms: Long, allDay: Boolean) = if (allDay) fmtDate(ms) else "${fmtDate(ms)} at ${fmtTime(ms)}"
private fun utcMillis(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun dateOfUtc(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

/** Ticks once a second, only while composed (the Dates tab is visible). */
@Composable
private fun rememberSecondNow(): Long {
    val state = produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000L + 5L)
            value = System.currentTimeMillis()
        }
    }
    return state.value
}

@Composable
fun DatesTab(draft: CountEvent, onChange: (CountEvent) -> Unit, onOpenDates: () -> Unit) {
    val d = remember(draft) { EventEditing.normalize(draft) }
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it.take(EventEditing.NAME_MAX))) },
            placeholder = { Text("Event name", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Event name" },
        )
        val (main, sub) = when (draft.kind) {
            EventKind.COUNTDOWN -> fmtDateTime(d.endAt ?: d.startAt, d.allDay) to "Countdown"
            EventKind.SINCE -> fmtDateTime(d.startAt, d.allDay) to "Time since"
            EventKind.TIMER -> EventEditing.totalText(d.startAt, d.endAt ?: d.startAt) to "Timer"
        }
        val rep = if (draft.repeat == EventRepeat.NONE) null else EventEditing.repeatLabel(draft.repeat)
        DaymarkCard(Modifier.fillMaxWidth(), onClick = onOpenDates, contentPadding = 16.dp) {
            Row(Modifier.semantics { contentDescription = "Dates: $main, $sub. Tap to change." }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(main, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(rep, sub).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        StatsGrid(d)
        if (noteOpen || draft.notes.isNotEmpty()) {
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { onChange(draft.copy(notes = it.take(2000))) },
                label = { Text("Note") },
                minLines = 3,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = { noteOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add note")
            }
        }
    }
}

@Composable
private fun StatsGrid(d: CountEvent) {
    val now = rememberSecondNow()
    val p = remember(d, now) { EventEngine.progress(d, now) }
    val cells = buildList {
        if (d.kind != EventKind.SINCE) {
            add("Finish" to (p.end?.let { fmtDateTime(it, d.allDay) } ?: "-"))
            add("Total" to (p.end?.let { EventEditing.totalText(p.start, it) } ?: "-"))
        } else {
            add("Start" to fmtDateTime(p.start, d.allDay))
        }
        add("Elapsed" to EventEditing.span(p.start, p.start + p.elapsedMs, seconds = true))
        if (d.kind != EventKind.SINCE) add("Remaining" to EventEditing.span(now, p.end ?: now, seconds = true).let { if (p.completed) "Done" else it })
    }
    DaymarkCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        cells.chunked(2).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.size(10.dp))
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (k, v) ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// region Full-screen date editor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateEditor(draft: CountEvent, isNew: Boolean, onChange: (CountEvent) -> Unit, onDone: () -> Unit) {
    val d = remember(draft) { EventEditing.normalize(draft) }
    var changeStart by rememberSaveable { mutableStateOf(false) }
    var repeatOpen by remember { mutableStateOf(false) }
    var pickStartDate by remember { mutableStateOf(false) }
    var pickStartTime by remember { mutableStateOf(false) }
    var pickEndTime by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                KindPill(draft.kind, isNew) { k -> onChange(EventEditing.convertKind(draft, k, System.currentTimeMillis())) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done", style = MaterialTheme.typography.titleMedium) }
            }
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (draft.kind) {
                    EventKind.COUNTDOWN -> {
                        val startDate = localDate(d.startAt)
                        DateCalendar(
                            key = "end$startDate", selected = localDate(d.endAt ?: d.startAt),
                            selectable = { it >= startDate },
                            onPick = { date -> onChange(draft.copy(endAt = EventEditing.withDate(d.endAt ?: d.startAt, date))) },
                        )
                    }
                    EventKind.SINCE -> DateCalendar(
                        key = "since", selected = localDate(d.startAt), selectable = { true },
                        onPick = { date ->
                            onChange(draft.copy(startAt = EventEditing.withDate(d.startAt, date)).let { if (it.endAt != null) it.copy(endAt = null) else it })
                        },
                    )
                    EventKind.TIMER -> {
                        DateCalendar(
                            key = "timer", selected = localDate(d.startAt), selectable = { true },
                            onPick = { date -> onChange(fix(draft.copy(startAt = EventEditing.withDate(d.startAt, date)))) },
                        )
                        TimerTiles(draft) { onChange(fix(it)) }
                    }
                }
                SwitchRow("All day event", draft.allDay, { on ->
                    onChange(
                        if (on) fix(draft.copy(allDay = true, startAt = EventEditing.startOfDay(d.startAt), endAt = d.endAt?.let { EventEditing.startOfDay(it) }.takeIf { draft.kind != EventKind.TIMER }))
                        else draft.copy(allDay = false),
                    )
                })
                if (!draft.allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TimeChip(if (draft.kind == EventKind.COUNTDOWN) "Start time" else "Time", fmtTime(d.startAt)) { pickStartTime = true }
                        if (draft.kind == EventKind.COUNTDOWN) TimeChip("End time", fmtTime(d.endAt ?: d.startAt)) { pickEndTime = true }
                    }
                }
                Summary(d, changeStart, { changeStart = !changeStart }, { pickStartDate = true }, { pickStartTime = true })
                if (draft.kind == EventKind.TIMER) {
                    SettingRow("Count direction") {
                        PillToggle(
                            listOf(
                                ToggleItem(false, "Count down", icon = Icons.Rounded.ArrowDownward),
                                ToggleItem(true, "Count up", icon = Icons.Rounded.ArrowUpward),
                            ),
                            draft.countUp,
                            { up -> onChange(draft.copy(countUp = up, options = draft.options.copy(accessory = com.daymark.app.domain.Accessory.AUTO))) },
                        )
                    }
                }
                if (draft.kind != EventKind.SINCE) {
                    val label = EventEditing.repeatLabel(draft.repeat)
                    DaymarkCard(Modifier.fillMaxWidth(), onClick = { repeatOpen = true }, contentPadding = 14.dp) {
                        Row(
                            Modifier.semantics { contentDescription = "Repeat: $label" }.heightIn(min = 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Repeat", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(label, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.size(16.dp))
            }
        }
    }

    if (repeatOpen) RepeatSheet(d, { onChange(draft.copy(repeat = it)); repeatOpen = false }, { repeatOpen = false })
    if (pickStartDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMillis(localDate(d.startAt)))
        DatePickerDialog(
            onDismissRequest = { pickStartDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val newStart = EventEditing.withDate(d.startAt, dateOfUtc(ms))
                        // keep the countdown's length when the start moves
                        val moved = if (draft.kind == EventKind.COUNTDOWN && d.endAt != null) draft.copy(startAt = newStart, endAt = d.endAt) else draft.copy(startAt = newStart)
                        onChange(fix(moved))
                    }
                    pickStartDate = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { pickStartDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (pickStartTime) TimeDialog(localTime(d.startAt), { t -> onChange(fix(draft.copy(startAt = EventEditing.withTime(d.startAt, t)))); pickStartTime = false }, { pickStartTime = false })
    if (pickEndTime) TimeDialog(localTime(d.endAt ?: d.startAt), { t -> onChange(draft.copy(endAt = EventEditing.withTime(d.endAt ?: d.startAt, t))); pickEndTime = false }, { pickEndTime = false })
}

/** Normalises the end of a timer and drops a repeat that no longer fits. */
private fun fix(e: CountEvent): CountEvent = EventEditing.fixRepeat(EventEditing.normalize(e))

@Composable
private fun KindPill(kind: EventKind, editable: Boolean, onPick: (EventKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val names = listOf(EventKind.COUNTDOWN to "Countdown", EventKind.SINCE to "Time since", EventKind.TIMER to "Timer")
    val label = names.first { it.first == kind }.second
    Box {
        Row(
            Modifier.heightIn(min = 48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (editable) Modifier.clickable(role = Role.DropdownList) { open = true } else Modifier)
                .semantics { contentDescription = if (editable) "Event type: $label. Tap to change." else "Event type: $label" }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (editable) Icon(Icons.Rounded.UnfoldMore, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            names.forEach { (k, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { open = false; onPick(k) }) }
        }
    }
}

@Composable
private fun TimeChip(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "$label $value. Tap to change." }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Summary(d: CountEvent, changeStart: Boolean, toggle: () -> Unit, pickDate: () -> Unit, pickTime: () -> Unit) {
    when (d.kind) {
        EventKind.SINCE -> {
            val now = rememberSecondNow()
            Text("Elapsed: ${EventEditing.span(d.startAt, maxOf(now, d.startAt), seconds = true)}", style = MaterialTheme.typography.bodyMedium)
        }
        EventKind.COUNTDOWN -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Start: ${fmtDateTime(d.startAt, d.allDay)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Total: ${d.endAt?.let { EventEditing.totalText(d.startAt, it) } ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = toggle, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (changeStart) "Hide" else "Change") }
            }
            if (changeStart) StartRow(d, pickDate, pickTime)
        }
        EventKind.TIMER -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Start", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            StartRow(d, pickDate, pickTime)
            Text("Finish: ${d.endAt?.let { fmtDateTime(it, d.allDay) } ?: "-"}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StartRow(d: CountEvent, pickDate: () -> Unit, pickTime: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = pickDate).semantics { contentDescription = "Start date ${fmtDate(d.startAt)}. Tap to change." }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(fmtDate(d.startAt), style = MaterialTheme.typography.titleSmall)
        }
        if (!d.allDay) TimeChip("Start time", fmtTime(d.startAt), pickTime)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateCalendar(key: String, selected: LocalDate, selectable: (LocalDate) -> Boolean, onPick: (LocalDate) -> Unit) {
    val state = androidx.compose.runtime.key(key) {
        rememberDatePickerState(
            initialSelectedDateMillis = utcMillis(selected),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = selectable(dateOfUtc(utcTimeMillis))
            },
        )
    }
    LaunchedEffect(state.selectedDateMillis) {
        val ms = state.selectedDateMillis ?: return@LaunchedEffect
        val date = dateOfUtc(ms)
        if (date != selected) onPick(date)
    }
    DatePicker(state, showModeToggle = false, title = null, headline = null, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun TimerTiles(draft: CountEvent, onChange: (CountEvent) -> Unit) {
    var editing by remember { mutableStateOf<DurationField?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DurationField.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { f ->
                    val v = EventEditing.durationOf(draft.options, f)
                    fun set(n: Int) = onChange(draft.copy(options = EventEditing.withDuration(draft.options, f, n)))
                    DaymarkCard(Modifier.weight(1f), contentPadding = 8.dp) {
                        Text(f.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { set(v - 1) }, enabled = v > 0) { Icon(Icons.Rounded.Remove, "Decrease ${f.label}") }
                            Text(
                                v.toString(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Button) { editing = f }
                                    .wrapCenter().semantics { contentDescription = "${f.label}: $v. Tap to type a number." },
                            )
                            IconButton(onClick = { set(v + 1) }, enabled = v < f.max) { Icon(Icons.Rounded.Add, "Increase ${f.label}") }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (!EventEditing.durationValid(draft.options)) {
            Text("Set at least one minute.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    editing?.let { f ->
        var text by remember { mutableStateOf(EventEditing.durationOf(draft.options, f).toString()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(f.label) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    supportingText = { Text("0 to ${f.max}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(draft.copy(options = EventEditing.withDuration(draft.options, f, text.toIntOrNull() ?: 0)))
                    editing = null
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}

private fun Modifier.wrapCenter(): Modifier = this.then(Modifier.padding(vertical = 12.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDone: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onDone(LocalTime.of(state.hour, state.minute)) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
    )
}

// endregion
