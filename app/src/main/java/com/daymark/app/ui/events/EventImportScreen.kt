package com.daymark.app.ui.events

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.CalendarEntry
import com.daymark.app.domain.CalendarImport
import com.daymark.app.domain.EventDefaults
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.DaymarkCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

private const val MONTHS_AHEAD = 12

/** Reads upcoming calendar instances once. Thin ContentResolver wrapper; throws SecurityException without permission. */
private fun queryCalendar(context: android.content.Context, now: Long): List<CalendarEntry> {
    val end = java.time.ZonedDateTime.now().plusMonths(MONTHS_AHEAD.toLong()).toInstant().toEpochMilli()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        android.content.ContentUris.appendId(it, now)
        android.content.ContentUris.appendId(it, end)
    }.build()
    val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY)
    val out = ArrayList<CalendarEntry>()
    context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
        while (c.moveToNext() && out.size < 1000) {
            CalendarImport.entry(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3) == 1)?.let(out::add)
        }
    }
    return out
}

private fun hasCalendar(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/**
 * One-time import from the device calendar. READ_CALENDAR is asked for only here, after a rationale; nothing is synced and
 * nothing leaves the phone. [onDone] gets the number of events created.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventImportScreen(vm: MainViewModel, onBack: () -> Unit, onDone: (Int) -> Unit) {
    val context = LocalContext.current
    val events by vm.events.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(hasCalendar(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) denied = true
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = hasCalendar(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    var loading by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf<List<CalendarEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        loading = true
        failed = false
        try {
            all = withContext(Dispatchers.IO) { queryCalendar(context, System.currentTimeMillis()) }
        } catch (e: Exception) {
            failed = true
            if (e is SecurityException) granted = false
        }
        loading = false
    }

    var query by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    val available = remember(all, events) { all?.let { CalendarImport.available(it, events, System.currentTimeMillis()) } }
    val shown = remember(available, query) { available?.let { CalendarImport.search(it, query) } }
    val count = picked.count { k -> available?.any { it.key == k } == true }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Import from calendar") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        when {
            !granted -> Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DaymarkCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
                    Text("Your calendar stays yours", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Daymark reads your calendar once, on this device only, to let you pick events to count down to. Nothing leaves your phone.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (denied) {
                    Text(
                        "Calendar access is off. You can allow it in settings whenever you like.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Allow calendar access in settings") }
                    TextButton(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Ask again")
                    }
                } else {
                    Button(
                        onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Continue") }
                }
            }
            loading || available == null && !failed -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            failed -> Text(
                "Couldn't read your calendar. Please try again later.", textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                OutlinedTextField(
                    query, { query = it }, label = { Text("Search") }, singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                if (shown.isNullOrEmpty()) {
                    Text(
                        if (available.isNullOrEmpty()) "No upcoming calendar events to import in the next $MONTHS_AHEAD months." else "Nothing matches your search.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                    Column(Modifier.weight(1f)) {}
                } else {
                    LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        items(shown, key = { it.key }) { e ->
                            val on = e.key in picked
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .clickable(role = Role.Checkbox) { picked = if (on) picked - e.key else picked + e.key }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(on, null)
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                                    Text(
                                        if (e.allDay) "${fmtDate(e.startAt)} · all day" else "${fmtDate(e.startAt)} · ${fmtTime(e.startAt)}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        val chosen = available.orEmpty().filter { it.key in picked }
                        chosen.forEach { vm.saveEvent(CalendarImport.toEvent(it, now, Random.nextInt(EventDefaults.presets.size))) }
                        onDone(chosen.size)
                    },
                    enabled = count > 0,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 52.dp),
                ) { Text(if (count == 1) "Import 1 event" else "Import $count events") }
            }
        }
    }
}
