package com.daymark.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.CountUnit
import com.daymark.app.domain.EventEditing
import com.daymark.app.domain.EventKind
import com.daymark.app.ui.components.DaymarkCard

/** How the card counts: time or percent, units, rounding, accessory words. */
@Composable
fun CountingTab(draft: CountEvent, onChange: (CountEvent) -> Unit) {
    val o = draft.options
    val hasEnd = draft.kind != EventKind.SINCE || draft.endAt != null
    val down = !draft.countUp && hasEnd
    val percent = o.showPercent && hasEnd
    var unitsOpen by remember { mutableStateOf(false) }
    fun opts(f: (com.daymark.app.domain.EventOptions) -> com.daymark.app.domain.EventOptions) = onChange(draft.copy(options = f(o)))

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DaymarkCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
            if (hasEnd) {
                SettingRow(if (percent) "Show percentage" else "Show time", "Show how much time is left or passed, or how far along the event is.") {
                    PillToggle(
                        listOf(
                            ToggleItem(false, "Show time", icon = Icons.Rounded.Schedule),
                            ToggleItem(true, "Show percentage", icon = Icons.Rounded.Percent),
                        ),
                        o.showPercent,
                        { p -> opts { it.copy(showPercent = p, accessory = if (p) com.daymark.app.domain.Accessory.NONE else com.daymark.app.domain.Accessory.AUTO) } },
                    )
                }
            } else {
                Text("Show time", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                CenteredHint("A time-since event with no end date has nothing to measure a percentage against.")
            }
            if (!percent) {
                Hairline()
                val unitLabel = when {
                    o.multiUnits in 2..5 -> mapOf(2 to "2 units (Y M)", 3 to "3 units (Y M D)", 4 to "4 units (Y M D H)", 5 to "5 units (Y M D H min)").getValue(o.multiUnits)
                    else -> singleName(o.singleUnit)
                }
                SettingRow("Time units", "The units shown as text. With a smaller amount than 2, a smaller unit is used, so you see \"36 hours left\" instead of \"1.5 days left\".") {
                    Row(
                        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { unitsOpen = true }
                            .semantics { contentDescription = "Time units, $unitLabel" }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(unitLabel, color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.Rounded.UnfoldMore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Hairline()
                val up = o.roundUpFor(!down)
                SettingRow(
                    if (up) "Round up" else "Round down",
                    "Round partial units. For example 4.3 days shows as 5 days when rounding up, or 4 days when rounding down.",
                ) {
                    PillToggle(
                        listOf(
                            ToggleItem(true, "Round up", icon = Icons.Rounded.ArrowUpward),
                            ToggleItem(false, "Round down", icon = Icons.Rounded.ArrowDownward),
                        ),
                        up, { r -> opts { it.copy(roundUp = r) } },
                    )
                }
                Hairline()
                SwitchRow(
                    "Use a smaller unit under 2", o.precision, { v -> opts { it.copy(precision = v) } },
                    helper = "Precision: switch this off to keep the unit you picked, even for small amounts.",
                )
            }
            Hairline()
            val choices = EventEditing.accessoryChoices(down, percent)
            val current = EventEditing.effectiveAccessory(o.accessory, down, percent)
            DropdownRow(
                "Accessory indicator", choices.first { it.first == current }.second, choices,
                { a -> opts { it.copy(accessory = a) } },
                helper = "The little words around the amount, such as \"7 days left\" or \"In 7 days\".",
            )
        }
        if (unitsOpen) UnitsSheet(o, { m, u -> opts { it.copy(multiUnits = m, singleUnit = u) }; unitsOpen = false }, { unitsOpen = false })
    }
}

private fun singleName(u: CountUnit) = when (u) {
    CountUnit.MINUTES -> "Minutes"; CountUnit.HOURS -> "Hours"; CountUnit.DAYS -> "Days"
    CountUnit.WEEKS -> "Weeks"; CountUnit.MONTHS -> "Months"; CountUnit.YEARS -> "Years"
}
