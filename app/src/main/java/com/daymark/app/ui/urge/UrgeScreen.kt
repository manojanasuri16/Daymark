package com.daymark.app.ui.urge

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.Options
import com.daymark.app.domain.UrgeRecord
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.WhenPicker
import com.daymark.app.ui.components.initialTimestamp
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.log.MoodButton
import com.daymark.app.ui.log.Section
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "I rode out an urge" — logging a craving that *didn't* become a slip. Shows the user's own reasons first,
 * because that's what helps in the moment. [editId] 0 = new.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UrgeScreen(vm: MainViewModel, editId: Long, initialDate: Long?, onClose: () -> Unit) {
    val haptics = rememberHaptics()
    val habit by vm.selectedHabit.collectAsStateWithLifecycle()
    val urges by vm.urges.collectAsStateWithLifecycle()

    var loaded by rememberSaveable { mutableStateOf(editId == 0L) }
    var timestamp by rememberSaveable { mutableLongStateOf(initialTimestamp(initialDate)) }
    var intensity by rememberSaveable { mutableStateOf(0) }
    var notes by rememberSaveable { mutableStateOf("") }
    val triggers = remember { mutableStateListOf<String>() }
    val custom = remember { mutableStateListOf<String>() }
    var original by remember { mutableStateOf<UrgeRecord?>(null) }
    var showCustom by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var celebrated by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(editId) {
        if (editId != 0L && original == null) {
            val u = vm.allUrges.value.firstOrNull { it.id == editId }
                ?: withTimeoutOrNull(3_000) { vm.allUrges.first { l -> l.any { it.id == editId } } }?.firstOrNull { it.id == editId }
            if (u != null) {
                original = u
                if (!loaded) {
                    timestamp = u.timestamp; intensity = u.intensity; notes = u.notes
                    triggers.clear(); triggers.addAll(u.triggers)
                    custom.clear(); custom.addAll(u.triggers.filter { it !in Options.triggers })
                    loaded = true
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (editId == 0L) "Riding out an urge" else "Edit urge") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
            actions = {
                if (editId != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (editId == 0L) {
                Text(
                    "Urges rise, peak and pass — usually within 15–20 minutes. Noticing one without acting on it is a win.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val reasons = habit?.reasons.orEmpty()
                if (reasons.isNotEmpty()) {
                    DaymarkCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FormatQuote, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Remember why", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.height(8.dp))
                        reasons.forEach {
                            Text("•  $it", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }

            Section("When") { WhenPicker(timestamp) { timestamp = minOf(it, System.currentTimeMillis()) } }

            Section("How strong was it?") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    INTENSITY.forEachIndexed { i, (emoji, label) ->
                        val v = i + 1
                        MoodButton(emoji, label, selected = intensity == v) { haptics.tick(); intensity = if (intensity == v) 0 else v }
                    }
                }
            }

            Section("What set it off?", hint = "Choose any that apply") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Options.triggers + custom).distinct().forEach { t ->
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
                        selected = false, onClick = { showCustom = true }, label = { Text("Other") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) }, shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            Section("What helped?", hint = "So you can do it again next time") {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it.take(2000) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    placeholder = { Text("Optional — e.g. went for a walk, called a friend, cold shower") },
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
                    vm.saveUrge(
                        UrgeRecord(
                            id = editId, habitId = original?.habitId ?: habit?.id ?: 1L, timestamp = timestamp,
                            intensity = intensity, triggers = triggers.toList(), notes = notes.trim(),
                        ),
                    )
                    if (editId != 0L) onClose() else celebrated = urges.size + 1
                },
                enabled = loaded,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (editId == 0L) "I rode it out" else "Save changes", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (showCustom) {
        var text by remember { mutableStateOf("") }
        val add = {
            val t = text.trim().replace("|", "/").take(30)
            if (t.isNotEmpty()) {
                if (t !in Options.triggers && t !in custom) custom.add(t)
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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
            },
            confirmButton = { TextButton(onClick = add) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this urge?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; original?.let { vm.deleteUrge(it) }; onClose() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }

    celebrated?.let { n ->
        AlertDialog(
            onDismissRequest = { celebrated = null; onClose() },
            icon = { Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("You got through it", textAlign = TextAlign.Center) },
            text = {
                Text(
                    if (n == 1) "Your first urge ridden out. Each one makes the next a little easier."
                    else "That's $n urges you've ridden out. Each one makes the next a little easier.",
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { Button(onClick = { celebrated = null; onClose() }, shape = RoundedCornerShape(14.dp)) { Text("Keep going") } },
        )
    }
}

private val INTENSITY = listOf("🌤️" to "Mild", "🌥️" to "Some", "☁️" to "Strong", "🌧️" to "Very", "⛈️" to "Intense")
