package com.daymark.app.ui.events

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventDefaults
import com.daymark.app.domain.EventDesign
import com.daymark.app.domain.EventEditing
import com.daymark.app.domain.EventKind
import com.daymark.app.domain.EventStarter
import com.daymark.app.domain.EventTemplate
import com.daymark.app.domain.EventTemplates
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.theme.Motion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

private const val S_STARTER = 0
private const val S_IDEAS = 1
private const val S_NAME = 2
private const val S_DATE = 3
private const val S_STYLE = 4

private val zone: ZoneId get() = ZoneId.systemDefault()
private val designLabels = mapOf(
    EventDesign.FONTY to "Text", EventDesign.SWISS to "Ring", EventDesign.GRID to "Grid", EventDesign.ROW to "Bars", EventDesign.MINIMAL to "Minimal",
)

/** A timer idea becomes a countdown in the wizard (timers need a duration, which the editor offers). */
private fun wizardKind(k: EventKind) = if (k == EventKind.TIMER) EventKind.COUNTDOWN else k

/**
 * The guided "new event" flow: what to count toward, (ideas), name, date, style, then a "ready" sheet.
 * [startAtIdeas] jumps straight to the ideas grid. Skippable at any step with the X.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EventWizardScreen(vm: MainViewModel, startAtIdeas: Boolean, onClose: () -> Unit, onOpenWidgetHelp: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(if (startAtIdeas) S_IDEAS else S_STARTER) }
    var ideasPath by rememberSaveable { mutableStateOf(startAtIdeas) }
    var starterId by rememberSaveable { mutableStateOf(if (startAtIdeas) "ideas" else "") }
    var templateName by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var kindId by rememberSaveable { mutableIntStateOf(EventKind.COUNTDOWN.id) }
    var dateDay by rememberSaveable { mutableLongStateOf(LocalDate.now().plusDays(30).toEpochDay()) }
    var allDay by rememberSaveable { mutableStateOf(true) }
    var minutes by rememberSaveable { mutableIntStateOf(9 * 60) }
    var preset by rememberSaveable { mutableIntStateOf(Random.nextInt(EventDefaults.presets.size)) }
    var designIdx by rememberSaveable { mutableIntStateOf(0) }
    var fontId by rememberSaveable { mutableStateOf("inter") }
    var created by remember { mutableStateOf<CountEvent?>(null) }
    var fontOpen by remember { mutableStateOf(false) }
    val now = rememberMinuteNow()
    val designs = EventDesign.entries

    val kind = EventKind.entries.first { it.id == kindId }
    val path = if (ideasPath) listOf(S_STARTER, S_IDEAS, S_NAME, S_DATE, S_STYLE) else listOf(S_STARTER, S_NAME, S_DATE, S_STYLE)
    val firstStep = if (startAtIdeas) S_IDEAS else S_STARTER

    fun draft(at: Long = System.currentTimeMillis()): CountEvent {
        val time = if (allDay) LocalTime.MIDNIGHT else LocalTime.ofSecondOfDay(minutes * 60L)
        val ms = LocalDate.ofEpochDay(dateDay).atTime(time).atZone(zone).toInstant().toEpochMilli()
        val base = if (kind == EventKind.SINCE) {
            CountEvent(name = name.trim(), kind = kind, startAt = ms, endAt = null, allDay = allDay, countUp = true)
        } else {
            CountEvent(name = name.trim(), kind = kind, startAt = at, endAt = ms, allDay = allDay, countUp = false)
        }
        val o = EventDefaults.optionsFor(kind, designs[designIdx], preset).copy(fontId = fontId)
        return base.copy(createdAt = at, modifiedAt = at, options = o)
    }

    fun pickStarter(s: EventStarter) {
        starterId = s.id
        ideasPath = s.ideas
        if (!s.ideas) {
            templateName = ""
            name = s.suggestedName
            kindId = s.kind.id
            dateDay = LocalDate.now().plusDays(if (s.kind == EventKind.SINCE) 0 else 30).toEpochDay()
        }
    }

    fun pickTemplate(t: EventTemplate) {
        templateName = t.name
        name = t.name
        val k = wizardKind(t.kind)
        kindId = k.id
        preset = t.preset
        designIdx = designs.indexOf(t.design).coerceAtLeast(0)
        dateDay = LocalDate.now().plusDays(if (k == EventKind.SINCE) 0 else 30).toEpochDay()
    }

    val dateOk = remember(step, name, kindId, dateDay, allDay, minutes) {
        val d = draft()
        EventEditing.isValid(d) && (d.kind != EventKind.SINCE || d.startAt <= System.currentTimeMillis())
    }
    val canNext = when (step) {
        S_STARTER -> starterId.isNotEmpty()
        S_IDEAS -> templateName.isNotEmpty()
        S_NAME -> name.isNotBlank()
        S_DATE -> dateOk
        else -> true
    }

    fun back() {
        val i = path.indexOf(step)
        if (step == firstStep || i <= 0) onClose() else step = path[i - 1]
    }
    fun next() {
        val i = path.indexOf(step)
        if (step == S_STYLE) {
            val e = draft()
            vm.saveEvent(e)
            created = e
        } else if (i >= 0 && i < path.lastIndex) step = path[i + 1]
    }
    BackHandler(enabled = created == null) { back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::back, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                LinearProgressIndicator(
                    progress = { (path.indexOf(step) + 1).coerceAtLeast(1) / path.size.toFloat() },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).clip(CircleShape),
                    drawStopIndicator = {},
                )
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, "Skip and close") }
            }
            AnimatedContent(
                step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val fwd = targetState > initialState
                    (slideInHorizontally(Motion.standard(0.4f)) { if (fwd) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally(Motion.standard(0.3f)) { if (fwd) -it / 4 else it / 4 } + fadeOut())
                },
                label = "wizardStep",
            ) { s ->
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    when (s) {
                        S_STARTER -> {
                            Title("What would you like to count toward?")
                            LazyVerticalGrid(
                                GridCells.Fixed(2), contentPadding = PaddingValues(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(EventTemplates.starters) { st ->
                                    val on = starterId == st.id
                                    Column(
                                        Modifier.fillMaxWidth().heightIn(min = 112.dp).clip(RoundedCornerShape(20.dp))
                                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                            .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(20.dp))
                                            .semantics { contentDescription = st.label; selected = on }
                                            .clickable(role = Role.RadioButton) { pickStarter(st) }.padding(16.dp),
                                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(st.emoji, style = MaterialTheme.typography.headlineLarge)
                                        Text(st.label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                                    }
                                }
                            }
                        }
                        S_IDEAS -> {
                            Title("Pick an idea to start from")
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(
                                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 48.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    EventTemplates.ideas.forEach { t ->
                                        val on = templateName == t.name
                                        Box(
                                            Modifier.heightIn(min = 48.dp).clip(CircleShape)
                                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                                .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                                .semantics { contentDescription = t.name; selected = on }
                                                .clickable(role = Role.RadioButton) { pickTemplate(t) }.padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) { Text(t.title, style = MaterialTheme.typography.labelLarge) }
                                    }
                                }
                                Box(
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(48.dp)
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
                                )
                            }
                        }
                        S_NAME -> {
                            Title("What should we call it?")
                            OutlinedTextField(
                                name, { name = it.take(EventEditing.NAME_MAX) },
                                label = { Text("Name") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            )
                        }
                        S_DATE -> DateStep(
                            name, kind, dateDay, { dateDay = it }, allDay, { allDay = it }, minutes, { minutes = it },
                        )
                        else -> {
                            Title("Choose a look")
                            val pager = rememberPagerState(initialPage = designIdx) { designs.size }
                            val setDesign by rememberUpdatedState { i: Int -> designIdx = i }
                            LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { setDesign(it) } }
                            val previewBase = draft()
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                                HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 24.dp), pageSpacing = 12.dp) { page ->
                                    EventCard(
                                        previewBase.copy(options = previewBase.options.copy(design = designs[page])), now,
                                        Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = "${designLabels[designs[page]]} design" },
                                    )
                                }
                                Text(
                                    designLabels[designs[pager.currentPage]] ?: "", style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                                    designs.forEachIndexed { i, _ ->
                                        val on = pager.currentPage == i
                                        Box(
                                            Modifier.padding(horizontal = 3.dp).size(if (on) 10.dp else 7.dp).clip(CircleShape)
                                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        )
                                    }
                                }
                                Text("Colours", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
                                @OptIn(ExperimentalLayoutApi::class)
                                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    EventDefaults.presets.forEachIndexed { i, p ->
                                        val on = i == preset
                                        Box(
                                            Modifier.size(48.dp).clip(CircleShape)
                                                .semantics { contentDescription = "${p.name} colour look${if (on) ", selected" else ""}" }
                                                .clickable(role = Role.Button) { preset = i },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                Modifier.size(34.dp).clip(CircleShape).background(Color(p.bg))
                                                    .border(if (on) 2.5.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(p.accent))) }
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = { fontOpen = true },
                                    modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).padding(top = 8.dp),
                                ) {
                                    Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(18.dp))
                                    Text("Font", modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = ::next, enabled = canNext && created == null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp),
            ) { Text(if (step == S_STYLE) "Create event" else "Continue") }
        }
    }

    if (fontOpen) FontSheet(fontId, { fontId = it; fontOpen = false }, { fontOpen = false })

    created?.let { e ->
        ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Your event is ready!", style = MaterialTheme.typography.titleLarge)
                EventCard(e, now, Modifier.fillMaxWidth().padding(vertical = 16.dp).height(170.dp))
                Text(
                    "You can now add it to your home screen widgets.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)) { Text("Awesome!") }
                TextButton(onClick = onOpenWidgetHelp, modifier = Modifier.heightIn(min = 48.dp)) { Text("Need help with widgets?") }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateStep(
    name: String, kind: EventKind, day: Long, onDay: (Long) -> Unit,
    allDay: Boolean, onAllDay: (Boolean) -> Unit, minutes: Int, onMinutes: (Int) -> Unit,
) {
    val since = kind == EventKind.SINCE
    var timeOpen by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return if (since) !d.isAfter(today) else d.isAfter(today)
            }
        },
    )
    LaunchedEffect(state.selectedDateMillis) {
        val ms = state.selectedDateMillis ?: return@LaunchedEffect
        onDay(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Title("What is the date of ${name.ifBlank { "your event" }}?")
        Text(
            if (since) "Pick the day it began. Past dates are fine." else "Pick the day you're counting down to.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        DatePicker(state, showModeToggle = false, title = null, headline = null, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("All-day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(allDay, onAllDay)
        }
        if (!allDay) {
            TextButton(onClick = { timeOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Time: %02d:%02d".format(minutes / 60, minutes % 60))
            }
        }
    }
    if (timeOpen) {
        val ts = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current))
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            confirmButton = { TextButton(onClick = { onMinutes(ts.hour * 60 + ts.minute); timeOpen = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("Cancel") } },
            text = { TimePicker(ts) },
        )
    }
}
