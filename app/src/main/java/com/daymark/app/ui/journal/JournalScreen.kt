package com.daymark.app.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.data.PhotoStore
import com.daymark.app.domain.JournalEntry
import com.daymark.app.ui.components.PhotoThumb
import com.daymark.app.ui.components.PhotoViewer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import com.daymark.app.domain.Options
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.WhenPicker
import com.daymark.app.ui.components.initialTimestamp
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.log.MoodButton
import com.daymark.app.ui.log.Section
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val PROMPTS = listOf(
    "What went well today?",
    "What was hard, and how did you handle it?",
    "What are you grateful for right now?",
    "What would help you tomorrow?",
    "How did you take care of yourself today?",
    "What did you notice about your urges today?",
)

/** A journal entry on any day — not only on slips. [editId] 0 = new. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(vm: MainViewModel, editId: Long, initialDate: Long?, onClose: () -> Unit) {
    val haptics = rememberHaptics()
    val habit by vm.selectedHabit.collectAsStateWithLifecycle()
    var loaded by rememberSaveable { mutableStateOf(editId == 0L) }
    var timestamp by rememberSaveable { mutableLongStateOf(initialTimestamp(initialDate)) }
    var mood by rememberSaveable { mutableStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var original by remember { mutableStateOf<JournalEntry?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Newline-joined so it survives process death.
    var photosText by rememberSaveable { mutableStateOf("") }
    val photos = photosText.split("\n").filter { it.isNotBlank() }
    var importing by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(PhotoStore.MAX_PER_ENTRY),
    ) { uris ->
        val room = PhotoStore.MAX_PER_ENTRY - photos.size
        if (uris.isNotEmpty() && room > 0) {
            importing = true
            scope.launch {
                val added = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    uris.take(room).mapNotNull { runCatching { PhotoStore.import(context, it) }.getOrNull() }
                }
                photosText = (photos + added).joinToString("\n")
                importing = false
            }
        }
    }
    val prompt = remember { PROMPTS[(java.time.LocalDate.now().toEpochDay() % PROMPTS.size).toInt()] }

    LaunchedEffect(editId) {
        if (editId != 0L && original == null) {
            val j = vm.allJournal.value.firstOrNull { it.id == editId }
                ?: withTimeoutOrNull(3_000) { vm.allJournal.first { l -> l.any { it.id == editId } } }?.firstOrNull { it.id == editId }
            if (j != null) {
                original = j
                if (!loaded) { timestamp = j.timestamp; mood = j.mood; text = j.text; photosText = j.photos.joinToString("\n"); loaded = true }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (editId == 0L) "Journal" else "Edit journal entry") },
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
            Section("When") { WhenPicker(timestamp) { timestamp = minOf(it, System.currentTimeMillis()) } }
            Section("How are you feeling?") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Options.moods.forEach { m ->
                        MoodButton(m.emoji, m.label, selected = mood == m.value) { haptics.tick(); mood = if (mood == m.value) 0 else m.value }
                    }
                }
            }
            Section(prompt, hint = "Private, on this device only") {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(5000) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    placeholder = { Text("Write as much or as little as you like") },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            Section("Photos", hint = "Optional · up to ${PhotoStore.MAX_PER_ENTRY} · kept inside Daymark, not added to your gallery") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    photos.forEach { name ->
                        PhotoThumb(name, 84.dp, onClick = { viewing = name }, onRemove = {
                            photosText = (photos - name).joinToString("\n")
                        })
                    }
                    if (photos.size < PhotoStore.MAX_PER_ENTRY) {
                        androidx.compose.material3.OutlinedCard(
                            onClick = {
                                picker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            enabled = !importing,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.size(84.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (importing) androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.AddPhotoAlternate, "Add photo", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                onClick = {
                    haptics.confirm()
                    vm.saveJournal(
                        JournalEntry(editId, original?.habitId ?: habit?.id ?: 1L, timestamp, mood, text.trim(), photos),
                        removedPhotos = original?.photos.orEmpty(),
                    )
                    onClose()
                },
                enabled = loaded && !importing && (text.isNotBlank() || mood != 0 || photos.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(if (editId == 0L) "Save entry" else "Save changes", style = MaterialTheme.typography.titleMedium) }
        }
    }

    viewing?.let { PhotoViewer(it) { viewing = null } }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this journal entry?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; original?.let { vm.deleteJournal(it) }; onClose() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
