package com.daymark.app.ui.events

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.data.Settings
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventFilter
import com.daymark.app.domain.EventKind
import com.daymark.app.domain.EventListLogic
import com.daymark.app.domain.EventSort
import com.daymark.app.domain.ItemSize
import com.daymark.app.notify.HabitReminders
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.theme.Motion

private fun filterLabel(f: EventFilter) = when (f) {
    EventFilter.ONGOING -> "Ongoing"
    EventFilter.COMPLETED -> "Completed"
    EventFilter.ALL -> "All"
    EventFilter.ARCHIVED -> "Archived"
}

private fun sortLabel(s: EventSort) = when (s) {
    EventSort.NAME -> "Name"
    EventSort.UPCOMING -> "Upcoming events"
    EventSort.PERCENT -> "% completed"
    EventSort.CREATED -> "Date created"
    EventSort.MODIFIED -> "Date modified"
}

/** Wording of the two directions of each sort (ascending first). */
private fun directionLabels(s: EventSort) = when (s) {
    EventSort.NAME -> "A to Z" to "Z to A"
    EventSort.UPCOMING -> "Soonest first" to "Latest first"
    EventSort.PERCENT -> "Lowest first" to "Highest first"
    EventSort.CREATED, EventSort.MODIFIED -> "Oldest first" to "Newest first"
}

private fun sizeLabel(s: ItemSize) = when (s) { ItemSize.SMALL -> "Small"; ItemSize.MEDIUM -> "Medium"; ItemSize.LARGE -> "Large" }

private fun columnsFor(s: ItemSize) = when (s) { ItemSize.SMALL -> 3; ItemSize.MEDIUM -> 2; ItemSize.LARGE -> 1 }
private fun aspectFor(s: ItemSize) = when (s) { ItemSize.SMALL -> 0.95f; ItemSize.MEDIUM -> 1.1f; ItemSize.LARGE -> 1.9f }

/** The Events tab: filter/sort/size row, a grid of event cards and a speed-dial button for new ones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventsScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    settings: Settings,
    onOpenEvent: (CountEvent) -> Unit,
    onNewEvent: (EventKind) -> Unit,
    onGiveIdeas: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleted: (CountEvent) -> Unit,
) {
    val context = LocalContext.current
    val events by vm.events.collectAsStateWithLifecycle()
    val now = rememberMinuteNow()
    LaunchedEffect(Unit) { vm.seedEventsIfNeeded() }

    val filter = settings.eventFilter
    val counts = remember(events, now) { EventListLogic.counts(events, now) }
    val shown = remember(events, filter, settings.eventSort, settings.eventSortAsc, now / 60_000L) {
        EventListLogic.sort(EventListLogic.filter(events, filter, now), settings.eventSort, settings.eventSortAsc, now)
    }
    var fabOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    val haptic = LocalHapticFeedback.current
    val showNag = !settings.batteryNagDismissed && !HabitReminders.isIgnoringBatteryOptimizations(context)
    val size = settings.eventCardSize

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsFor(size)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 104.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Events", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                        OverflowMenu(onOpenSettings = onOpenSettings, onGiveIdeas = onGiveIdeas)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterPill(filter, counts) { vm.setEventFilter(it) }
                        SortPill(settings.eventSort, settings.eventSortAsc) { s, asc -> vm.setEventSort(s, asc) }
                        Spacer(Modifier.weight(1f))
                        SizeButton(size) { vm.setEventCardSize(it) }
                    }
                }
            }
            if (showNag) {
                item(key = "battery-nag", span = { GridItemSpan(maxLineSpan) }) {
                    BatteryNag(onDismiss = { vm.setBatteryNagDismissed(true) }, onSetUp = { openBatterySettings(context) })
                }
            }
            if (shown.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        filtered = events.isNotEmpty(),
                        onCreate = { onNewEvent(EventKind.COUNTDOWN) },
                        onGiveIdeas = onGiveIdeas,
                    )
                }
            }
            items(shown, key = { it.id }) { event ->
                val source = remember { MutableInteractionSource() }
                Box(Modifier.animateItem()) {
                    EventCard(
                        event, now,
                        Modifier.fillMaxWidth().aspectRatio(aspectFor(size))
                            .pressScale(source)
                            .semantics {
                                role = Role.Button
                                customActions = listOf(
                                    CustomAccessibilityAction("More actions") { menuFor = event.id; true },
                                )
                            }
                            .combinedClickable(
                                interactionSource = source, indication = null,
                                onClickLabel = "Edit event",
                                onClick = { onOpenEvent(event) },
                                onLongClickLabel = "More actions",
                                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuFor = event.id },
                            ),
                    )
                    DropdownMenu(expanded = menuFor == event.id, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuFor = null; onOpenEvent(event) })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuFor = null; vm.duplicateEvent(event) })
                        DropdownMenuItem(
                            text = { Text(if (event.archived) "Unarchive" else "Archive") },
                            onClick = { menuFor = null; vm.archiveEvent(event.id, !event.archived) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuFor = null; vm.deleteEvent(event); onDeleted(event) },
                        )
                    }
                }
            }
        }

        // Dim the list while the speed dial is open; tapping it closes the dial.
        AnimatedVisibility(fabOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize()
                    .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { fabOpen = false })
                    .semantics { contentDescription = "Close menu" }
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
            )
        }
        SpeedDial(
            open = fabOpen,
            onToggle = { fabOpen = !fabOpen },
            onPick = { fabOpen = false; onNewEvent(it) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(
                end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        )
    }
}

@Composable
private fun OverflowMenu(onOpenSettings: () -> Unit, onGiveIdeas: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Give me ideas") }, onClick = { open = false; onGiveIdeas() })
            DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; onOpenSettings() })
        }
    }
}

@Composable
private fun Pill(label: String, trailing: ImageVector, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(start = 14.dp, end = 8.dp),
        modifier = Modifier.height(44.dp).semantics { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Spacer(Modifier.width(2.dp))
        Icon(trailing, null, Modifier.size(20.dp))
    }
}

@Composable
private fun FilterPill(current: EventFilter, counts: Map<EventFilter, Int>, onPick: (EventFilter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pill(filterLabel(current), Icons.Rounded.KeyboardArrowDown, "Filter: ${filterLabel(current)}") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EventFilter.entries.forEach { f ->
                if (f == EventFilter.ARCHIVED) HorizontalDivider()
                val n = counts[f] ?: 0
                val empty = n == 0 && f != EventFilter.ALL
                DropdownMenuItem(
                    enabled = !empty || f == current,
                    text = { Text(if (empty) "${filterLabel(f)} (none)" else filterLabel(f)) },
                    trailingIcon = { if (f == current) Icon(Icons.Rounded.Check, contentDescription = "Selected") },
                    onClick = { open = false; onPick(f) },
                )
            }
        }
    }
}

@Composable
private fun SortPill(current: EventSort, asc: Boolean, onPick: (EventSort, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var sub by remember { mutableStateOf<EventSort?>(null) }
    Box {
        Pill(
            sortLabel(current), if (asc) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            "Sort: ${sortLabel(current)}, ${directionLabels(current).let { if (asc) it.first else it.second }}",
        ) { sub = null; open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val s = sub
            if (s == null) {
                EventSort.entries.forEach { e ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(e), style = MaterialTheme.typography.titleSmall) },
                        trailingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        onClick = { sub = e },
                    )
                }
            } else {
                DropdownMenuItem(
                    text = { Text(sortLabel(s), style = MaterialTheme.typography.titleSmall) },
                    leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, "Back to sort options") },
                    onClick = { sub = null },
                )
                HorizontalDivider()
                val (a, d) = directionLabels(s)
                listOf(true to a, false to d).forEach { (isAsc, label) ->
                    val selected = s == current && isAsc == asc
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { Icon(if (isAsc) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null) },
                        trailingIcon = { if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected") },
                        onClick = { open = false; onPick(s, isAsc) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SizeButton(current: ItemSize, onPick: (ItemSize) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(14.dp),
            color = androidx.compose.ui.graphics.Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.size(44.dp).semantics { contentDescription = "Card size: ${sizeLabel(current)}" },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.GridView, null, Modifier.size(22.dp)) }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ItemSize.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(sizeLabel(s)) },
                    trailingIcon = { if (s == current) Icon(Icons.Rounded.Check, contentDescription = "Selected") },
                    onClick = { open = false; onPick(s) },
                )
            }
        }
    }
}

@Composable
private fun BatteryNag(onDismiss: () -> Unit, onSetUp: () -> Unit) {
    DaymarkCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.BatterySaver, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Keep widgets fresh", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Widgets refresh best when Daymark may run in the background. Allow unrestricted battery use for this app.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onSetUp) { Text("Open battery settings") }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss") }
        }
    }
}

private fun openBatterySettings(context: Context) {
    val list = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(list)
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) { /* nowhere to go; the card stays so the user can dismiss it */ }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, onCreate: () -> Unit, onGiveIdeas: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.HourglassTop, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            if (filtered) "Nothing here yet" else "Count toward something",
            style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (filtered) "Try another filter, or add a new event."
            else "A trip, a birthday, a fresh start. Make an event and watch the time go by.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreate) { Text("Create an event") }
        TextButton(onClick = onGiveIdeas) { Text("Give me ideas") }
    }
}

@Composable
private fun SpeedDial(open: Boolean, onToggle: () -> Unit, onPick: (EventKind) -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (open) 45f else 0f, Motion.bouncy(), label = "fab-rotation")
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val options = listOf(
            Triple(EventKind.COUNTDOWN, "Countdown", Icons.Rounded.HourglassTop),
            Triple(EventKind.SINCE, "Time since", Icons.Rounded.Timelapse),
            Triple(EventKind.TIMER, "Timer", Icons.Rounded.Timer),
        )
        options.forEach { (kind, label, icon) ->
            AnimatedVisibility(
                visible = open,
                enter = fadeIn() + scaleIn(Motion.bouncy(), initialScale = 0.6f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)) +
                    slideInVertically(Motion.standard()) { it / 2 },
                exit = fadeOut() + scaleOut(Motion.standard(), targetScale = 0.6f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)) +
                    slideOutVertically(Motion.standard()) { it / 2 },
            ) {
                Surface(
                    onClick = { onPick(kind) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.height(48.dp).semantics { contentDescription = "New $label event" },
                ) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        val source = remember { MutableInteractionSource() }
        Surface(
            onClick = onToggle,
            interactionSource = source,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 4.dp,
            modifier = Modifier.size(60.dp).pressScale(source).semantics {
                contentDescription = if (open) "Close new event menu" else "New event"
                stateDescription = if (open) "Open" else "Closed"
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, Modifier.size(28.dp).graphicsRotation(rotation))
            }
        }
    }
}

private fun Modifier.graphicsRotation(deg: Float) = this.rotate(deg)
