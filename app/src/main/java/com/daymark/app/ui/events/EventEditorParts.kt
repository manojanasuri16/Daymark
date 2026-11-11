package com.daymark.app.ui.events

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daymark.app.ui.theme.Motion

/** One segment of a [PillToggle]: an icon, a label, or both. [desc] is read by screen readers. */
data class ToggleItem<T>(val value: T, val desc: String, val label: String? = null, val icon: ImageVector? = null)

/** Two or three segments in a rounded pill; the chosen one is filled with the theme's container colour. */
@Composable
fun <T> PillToggle(items: List<ToggleItem<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { item ->
            val on = item.value == selected
            val bg by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, Motion.standard(0.3f), label = "pill",
            )
            val fg = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 42.dp).clip(CircleShape).background(bg)
                    .semantics { contentDescription = item.desc; role = Role.RadioButton; this.selected = on }
                    .clickable { onSelect(item.value) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                item.icon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(20.dp)) }
                if (item.icon != null && item.label != null) Spacer(Modifier.width(6.dp))
                item.label?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = fg) }
            }
        }
    }
}

/** A row with a title (and optional helper text underneath) and a control on the right. */
@Composable
fun SettingRow(title: String, helper: String? = null, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(end = 12.dp))
            control()
        }
        if (helper != null) {
            Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, helper: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) { stateDescription = if (checked) "On" else "Off" }
                .clickable(role = Role.Switch) { onChange(!checked) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
        if (helper != null) {
            Text(helper, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Label    value up-down" row that opens a small dropdown of [options]. */
@Composable
fun <T> DropdownRow(title: String, value: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit, helper: String? = null) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, helper) {
        Box {
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.DropdownList) { open = true }
                    .semantics { contentDescription = "$title, $value" }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Rounded.UnfoldMore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (v, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onSelect(v); open = false },
                        trailingIcon = if (label == value) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    )
                }
            }
        }
    }
}

/** A colour swatch circle; [ring] marks the selected one. */
@Composable
fun ColorDot(color: Color, size: androidx.compose.ui.unit.Dp, ring: Boolean, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(maxOf(size, 48.dp)).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc; this.selected = ring },
        contentAlignment = Alignment.Center,
    ) {
        val outline = MaterialTheme.colorScheme.outlineVariant
        Box(
            Modifier.size(size).clip(CircleShape).background(color).border(1.dp, outline, CircleShape),
        )
        if (ring) {
            Box(Modifier.size(size + 8.dp).border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

/** A titled group inside a tab. */
@Composable
fun EditorGroup(title: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        content()
    }
}

@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
}

@Composable
fun CenteredHint(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    )
}
