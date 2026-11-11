package com.daymark.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventDefaults
import com.daymark.app.domain.EventKind
import com.daymark.app.domain.EventOptions
import com.daymark.app.ui.MainViewModel

/** A fresh event of [kind] with sensible dates and style (also used as the editor's starting point). */
fun newEventDraft(kind: EventKind, now: Long = System.currentTimeMillis()): CountEvent {
    val week = 7L * 24 * 60 * 60 * 1000
    val options: EventOptions = EventDefaults.optionsFor(kind)
    return CountEvent(
        name = "",
        kind = kind,
        startAt = now,
        endAt = if (kind == EventKind.SINCE) null else now + week,
        allDay = false,
        countUp = kind == EventKind.SINCE,
        createdAt = now,
        modifiedAt = now,
        options = options,
    )
}

/**
 * Simple editor: a live preview, a name and Save. The full editor (dates, counting, style) replaces the body
 * later; the signature stays stable. [eventId] 0 creates a new event of [kind].
 */
@Composable
fun EventEditorScreen(vm: MainViewModel, eventId: Long, kind: EventKind?, onClose: () -> Unit) {
    val events by vm.events.collectAsStateWithLifecycle()
    val existing = remember(events, eventId) { events.firstOrNull { it.id == eventId } }
    val base = remember(existing, kind) { existing ?: newEventDraft(kind ?: EventKind.COUNTDOWN) }
    var name by remember(base.id) { mutableStateOf(base.name) }
    val now = rememberMinuteNow()
    val draft = base.copy(name = name)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            Text(
                if (existing == null) "New event" else "Edit event",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
        EventCard(draft, now, Modifier.fillMaxWidth().aspectRatio(1.6f))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.saveEvent(draft.copy(name = name.trim())); onClose() },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}
