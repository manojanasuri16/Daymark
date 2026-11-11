package com.daymark.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventDefaults
import com.daymark.app.domain.EventEditing
import com.daymark.app.domain.EventRepeat
import com.daymark.app.domain.EventKind
import com.daymark.app.domain.EventOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

/** 40-swatch grid plus a custom HSV colour. [onPick] is called live. */
@Composable
fun ColorSheet(title: String, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val palette = EventDefaults.palette
    var custom by rememberSaveable { mutableStateOf(false) }
    Sheet(title, onDismiss) {
        // 8 hue columns of 5 tonal steps
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (col in 0 until 8) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (row in 0 until 5) {
                        val c = palette[col * 5 + row]
                        ColorDot(Color(c), 30.dp, c == current, "Colour ${EventEditing.toHex(c)}") { onPick(c) }
                    }
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        TextButton(onClick = { custom = !custom }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (custom) "Hide custom colour" else "Custom colour")
        }
        if (custom) CustomColor(current, onPick)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Done") } }
    }
}

@Composable
private fun CustomColor(current: Int, onPick: (Int) -> Unit) {
    val start = remember { EventEditing.toHsv(current) }
    var h by remember { mutableFloatStateOf(start[0]) }
    var s by remember { mutableFloatStateOf(start[1]) }
    var v by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(EventEditing.toHex(current)) }
    var hexError by remember { mutableStateOf(false) }
    fun push() {
        val c = EventEditing.fromHsv(h, s, v)
        hex = EventEditing.toHex(c)
        hexError = false
        onPick(c)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(EventEditing.fromHsv(h, s, v))).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
        )
        // Rainbow track behind the hue slider would need custom drawing; the slider labels are enough here.
        Text("Hue", style = MaterialTheme.typography.labelMedium)
        Slider(h, { h = it; push() }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = "Hue" })
        Text("Saturation", style = MaterialTheme.typography.labelMedium)
        Slider(s, { s = it; push() }, valueRange = 0f..1f, modifier = Modifier.semantics { contentDescription = "Saturation" })
        Text("Brightness", style = MaterialTheme.typography.labelMedium)
        Slider(v, { v = it; push() }, valueRange = 0f..1f, modifier = Modifier.semantics { contentDescription = "Brightness" })
        OutlinedTextField(
            value = hex,
            onValueChange = { t ->
                hex = t.take(9)
                val c = EventEditing.parseHex(t)
                hexError = c == null
                if (c != null) {
                    val hsv = EventEditing.toHsv(c)
                    h = hsv[0]; s = hsv[1]; v = hsv[2]
                    onPick(c)
                }
            },
            label = { Text("Hex") },
            isError = hexError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun FontSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Sheet("Font", onDismiss) {
        EventFonts.all.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { f ->
                    val on = f.id == current
                    Row(
                        Modifier.weight(1f).heightIn(min = 52.dp).clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .semantics { contentDescription = "Font ${f.label}"; selected = on }
                            .clickable(role = Role.RadioButton) { onPick(f.id) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            f.label, style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily(EventFonts.typeface(context, f.id, false)),
                            maxLines = 1, modifier = Modifier.weight(1f),
                        )
                        if (on) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun IconSheet(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Sheet("Icon", onDismiss) {
        EventIcons.all.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { ic ->
                    val on = ic.id == current
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .semantics { contentDescription = "${ic.label} icon"; selected = on }
                            .clickable(role = Role.RadioButton) { onPick(ic.id) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(ic.vector(), null, modifier = Modifier.size(26.dp)) }
                }
                repeat(5 - row.size) { Spacer(Modifier.size(56.dp)) }
            }
        }
    }
}

@Composable
fun RepeatSheet(event: CountEvent, onPick: (EventRepeat) -> Unit, onDismiss: () -> Unit) {
    val options = remember(event) { EventEditing.repeatOptions(event) }
    val n = remember(event) { EventEditing.normalize(event) }
    val blocked = options.any { !it.enabled }
    Sheet(if (event.kind == EventKind.TIMER) "Repeat timer" else "Repeat countdown", onDismiss) {
        options.forEach { o ->
            val on = event.repeat == o.repeat
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                    .then(if (o.enabled) Modifier.clickable(role = Role.RadioButton) { onPick(o.repeat) } else Modifier)
                    .semantics { selected = on }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        EventEditing.repeatLabel(o.repeat), style = MaterialTheme.typography.bodyLarge,
                        color = if (o.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                    if (o.repeat == EventRepeat.ON_COMPLETION) {
                        Text("Restarts the timer by itself when it ends", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!o.enabled) Icon(Icons.Rounded.Warning, "Unavailable", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                else if (on) Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (blocked) {
            Spacer(Modifier.size(8.dp))
            val total = n.endAt?.let { EventEditing.totalText(n.startAt, it) } ?: ""
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Some options are unavailable because your event lasts longer than that interval. " +
                        "Make the event shorter to use them. Current total time: $total.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Time-units picker: multiple units (2-5) and single units. */
@Composable
fun UnitsSheet(options: EventOptions, onPick: (multi: Int, single: com.daymark.app.domain.CountUnit) -> Unit, onDismiss: () -> Unit) {
    val multi = listOf(2 to "Y M", 3 to "Y M D", 4 to "Y M D H", 5 to "Y M D H min")
    val single = listOf(
        com.daymark.app.domain.CountUnit.MINUTES to "Minutes", com.daymark.app.domain.CountUnit.HOURS to "Hours",
        com.daymark.app.domain.CountUnit.DAYS to "Days", com.daymark.app.domain.CountUnit.WEEKS to "Weeks",
        com.daymark.app.domain.CountUnit.MONTHS to "Months", com.daymark.app.domain.CountUnit.YEARS to "Years",
    )
    Sheet("Time units", onDismiss) {
        Text("Multiple units", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        multi.forEach { (n, hint) ->
            UnitRow("$n units", hint, options.multiUnits == n) { onPick(n, options.singleUnit) }
        }
        Spacer(Modifier.size(12.dp))
        Text("Single units", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        single.forEach { (u, label) ->
            UnitRow(label, null, options.multiUnits !in 2..5 && options.singleUnit == u) { onPick(0, u) }
        }
    }
}

@Composable
private fun UnitRow(label: String, hint: String?, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
            .semantics { selected = on }.clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (on) { Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.primary) }
    }
}
