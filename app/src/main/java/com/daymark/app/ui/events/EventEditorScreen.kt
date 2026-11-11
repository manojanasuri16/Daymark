package com.daymark.app.ui.events

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.daymark.app.widget.EventWidgetPinner
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventDefaults
import com.daymark.app.domain.EventDraftCodec
import com.daymark.app.domain.EventEditing
import com.daymark.app.domain.EventKind
import com.daymark.app.domain.EventOptions
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.theme.Motion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * A fresh event of [kind] with sensible dates (countdown: in a week; since: a week ago; timer: one week) and a
 * pleasant colour look. Also the editor's starting point for new events.
 */
fun newEventDraft(kind: EventKind, now: Long = System.currentTimeMillis(), presetIndex: Int = Random.nextInt(EventDefaults.presets.size)): CountEvent {
    val week = 7L * 24 * 60 * 60 * 1000
    val options: EventOptions = EventDefaults.optionsFor(kind, presetIndex = presetIndex)
    return CountEvent(
        name = "",
        kind = kind,
        startAt = if (kind == EventKind.SINCE) now - week else now,
        endAt = if (kind == EventKind.SINCE) null else now + week,
        allDay = true,
        countUp = kind == EventKind.SINCE,
        createdAt = now,
        modifiedAt = now,
        options = options,
    )
}

private val DraftSaver = Saver<CountEvent, String>(save = { EventDraftCodec.encode(it) }, restore = { EventDraftCodec.decode(it) })

/**
 * The full event editor: live preview, Dates / Counting / Style tabs and an overflow for saved events.
 * [eventId] 0 creates a new event of [kind]. [onDeleted] lets the host offer an undo.
 */
@Composable
fun EventEditorScreen(vm: MainViewModel, eventId: Long, kind: EventKind?, onClose: () -> Unit, onDeleted: (CountEvent) -> Unit = {}) {
    val events by vm.events.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf<CountEvent?>(null) }
    var gaveUp by remember { mutableStateOf(false) }
    val found = events.firstOrNull { it.id == eventId }
    if (loaded == null && found != null) loaded = found
    LaunchedEffect(eventId) {
        if (eventId != 0L && loaded == null) {
            withTimeoutOrNull(3000) { vm.events.first { l -> l.any { it.id == eventId } } }
            if (loaded == null && vm.events.value.none { it.id == eventId }) gaveUp = true
        }
    }
    LaunchedEffect(gaveUp) { if (gaveUp) onClose() }

    val initial = if (eventId == 0L) remember(kind) { newEventDraft(kind ?: EventKind.COUNTDOWN) } else loaded
    if (initial == null) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    EditorBody(vm, initial, isNew = eventId == 0L, onClose = onClose, onDeleted = onDeleted)
}

@Composable
private fun EditorBody(vm: MainViewModel, initial: CountEvent, isNew: Boolean, onClose: () -> Unit, onDeleted: (CountEvent) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(initial) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var datesOpen by rememberSaveable { mutableStateOf(false) }
    var discardAsk by remember { mutableStateOf(false) }
    var deleteAsk by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val now = rememberMinuteNow()

    val normalized = remember(draft) { EventEditing.normalize(draft) }
    val valid = remember(draft) { EventEditing.isValid(draft) }
    val dirty = remember(draft, initial, isNew) {
        if (isNew) draft.name.isNotBlank() || draft.notes.isNotBlank()
        else EventEditing.normalize(draft).copy(modifiedAt = 0) != EventEditing.normalize(initial).copy(modifiedAt = 0)
    }
    val canSave = valid && (isNew || dirty)
    val dateProblem = draft.kind != EventKind.SINCE && (normalized.endAt ?: 0L) <= normalized.startAt ||
        draft.kind == EventKind.SINCE && normalized.endAt != null && normalized.endAt <= normalized.startAt
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    fun tryClose() { if (dirty) discardAsk = true else onClose() }
    BackHandler { if (datesOpen) datesOpen = false else tryClose() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::tryClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                    Text(
                        if (isNew) "New event" else "Edit event", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    IconButton(onClick = { scope.launch { EventShare.share(context, normalized.copy(name = draft.name.trim())) } }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share as image")
                    }
                    TextButton(
                        onClick = {
                            vm.saveEvent(normalized.copy(name = draft.name.trim(), notes = draft.notes.trim()))
                            onClose()
                        },
                        enabled = canSave,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
                }
                AnimatedVisibility(!imeVisible, enter = fadeIn(Motion.standard()), exit = fadeOut(Motion.standard())) {
                    EventCard(
                        normalized.copy(name = draft.name), now,
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(176.dp),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Crossfade(tab, animationSpec = Motion.standard(0.3f), label = "tab") { t ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (t) {
                                    0 -> {
                                        DatesTab(draft, { draft = it }, { datesOpen = true })
                                        if (dateProblem) {
                                            Text(
                                                if (draft.kind == EventKind.TIMER) "Set a duration of at least one minute." else "The end needs to be after the start.",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                    1 -> CountingTab(draft) { draft = it }
                                    else -> StyleTab(draft) { draft = it }
                                }
                            }
                        }
                    }
                }
                TabBar(tab, { tab = it }, showOverflow = !isNew, menuOpen = menuOpen, onMenu = { menuOpen = it }) { action ->
                    menuOpen = false
                    when (action) {
                        Overflow.WIDGET -> EventWidgetPinner.pinEventWidget(context, initial.id) { EventWidgetPinner.message(it)?.let(vm::say) }
                        Overflow.DUPLICATE -> { vm.duplicateEvent(initial); onClose() }
                        Overflow.ARCHIVE -> { vm.archiveEvent(initial.id, !initial.archived); onClose() }
                        Overflow.DELETE -> deleteAsk = true
                    }
                }
            }
            AnimatedVisibility(
                datesOpen,
                enter = slideInVertically(Motion.standard(0.4f)) { it / 3 } + fadeIn(),
                exit = slideOutVertically(Motion.standard(0.35f)) { it / 3 } + fadeOut(),
            ) { DateEditor(draft, isNew, { draft = it }, { datesOpen = false }) }
        }
    }

    if (discardAsk) {
        AlertDialog(
            onDismissRequest = { discardAsk = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this event haven't been saved.") },
            confirmButton = { TextButton(onClick = { discardAsk = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { discardAsk = false }) { Text("Keep editing") } },
        )
    }
    if (deleteAsk) {
        AlertDialog(
            onDismissRequest = { deleteAsk = false },
            title = { Text("Delete this event?") },
            text = { Text("You can undo right after.") },
            confirmButton = {
                TextButton(onClick = { deleteAsk = false; vm.deleteEvent(initial); onDeleted(initial); onClose() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteAsk = false }) { Text("Cancel") } },
        )
    }
}

private enum class Overflow { WIDGET, DUPLICATE, ARCHIVE, DELETE }

@Composable
private fun TabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    showOverflow: Boolean,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    onAction: (Overflow) -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.shadow(6.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TabButton(Icons.Rounded.CalendarMonth, "Dates", selected == 0) { onSelect(0) }
            TabButton(Icons.Rounded.Straighten, "Counting", selected == 1) { onSelect(1) }
            TabButton(Icons.Rounded.Palette, "Style", selected == 2) { onSelect(2) }
            if (showOverflow) {
                Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Box {
                    IconButton(onClick = { onMenu(true) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.MoreVert, "More actions") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                        DropdownMenuItem(text = { Text("Add to home screen") }, onClick = { onAction(Overflow.WIDGET) })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { onAction(Overflow.DUPLICATE) })
                        DropdownMenuItem(text = { Text("Archive or unarchive") }, onClick = { onAction(Overflow.ARCHIVE) })
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { onAction(Overflow.DELETE) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val bg = if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
    val fg = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.heightIn(min = 48.dp).clip(CircleShape).background(bg)
            .semantics { contentDescription = "$label tab"; this.selected = on }
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = if (on) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg)
        if (on) { Text(label, style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.padding(start = 6.dp)) }
    }
}
