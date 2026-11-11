package com.daymark.app.ui.events

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlignHorizontalCenter
import androidx.compose.material.icons.rounded.AlignHorizontalLeft
import androidx.compose.material.icons.rounded.AlignHorizontalRight
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventAlign
import com.daymark.app.domain.EventDefaults
import com.daymark.app.domain.EventDesign
import com.daymark.app.domain.EventOptions
import com.daymark.app.domain.FutureStyle
import com.daymark.app.domain.ItemShape
import com.daymark.app.domain.ItemSize
import com.daymark.app.domain.TileShape
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.theme.Motion

private val designNames = mapOf(
    EventDesign.FONTY to "Text", EventDesign.SWISS to "Ring", EventDesign.GRID to "Grid", EventDesign.ROW to "Bars", EventDesign.MINIMAL to "Minimal",
)

private fun sizeName(s: ItemSize) = when (s) { ItemSize.SMALL -> "Small"; ItemSize.MEDIUM -> "Medium"; ItemSize.LARGE -> "Large" }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StyleTab(draft: CountEvent, onChange: (CountEvent) -> Unit) {
    val o = draft.options
    fun opts(f: (EventOptions) -> EventOptions) = onChange(draft.copy(options = f(draft.options)))
    val designs = EventDesign.entries
    val pager = rememberPagerState(initialPage = designs.indexOf(o.design)) { designs.size }
    val now = rememberMinuteNow()
    val latest by rememberUpdatedState(draft)
    val latestChange by rememberUpdatedState(onChange)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val cur = latest
            if (designs[page] != cur.options.design) latestChange(cur.copy(options = cur.options.copy(design = designs[page])))
        }
    }
    var colorTarget by rememberSaveable { mutableStateOf(-1) }
    var fontOpen by remember { mutableStateOf(false) }
    var iconOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EditorGroup("Design") {
            HorizontalPager(pager, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 28.dp), pageSpacing = 12.dp) { page ->
                EventCard(
                    draft.copy(options = o.copy(design = designs[page])), now,
                    Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "${designNames[designs[page]]} design" },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                designs.forEachIndexed { i, dsn ->
                    val on = pager.currentPage == i
                    Box(
                        Modifier.heightIn(min = 24.dp).padding(horizontal = 3.dp).size(if (on) 10.dp else 7.dp).clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Text(
                designNames[o.design] ?: "", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        DaymarkCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
            when (o.design) {
                EventDesign.FONTY -> {
                    SettingRow("Alignment") {
                        PillToggle(
                            listOf(
                                ToggleItem(EventAlign.START, "Align left", icon = Icons.Rounded.AlignHorizontalLeft),
                                ToggleItem(EventAlign.CENTER, "Align centre", icon = Icons.Rounded.AlignHorizontalCenter),
                                ToggleItem(EventAlign.END, "Align right", icon = Icons.Rounded.AlignHorizontalRight),
                            ),
                            o.align, { a -> opts { it.copy(align = a) } },
                        )
                    }
                    Hairline()
                    SwitchRow("Show details", o.showDetails, { v -> opts { it.copy(showDetails = v) } }, "Shows the date at the bottom of the card.")
                }
                EventDesign.SWISS -> Text(
                    "A ring that fills as time passes. Pick its colours and direction below.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EventDesign.GRID, EventDesign.ROW -> {
                    SwitchRow(
                        "Match items with time", o.matchItems, { v -> opts { it.copy(matchItems = v) } },
                        "Draws one item for every time unit selected on the Counting tab, for example one circle each day. Partial units count as a full item, so 11.2 days is drawn as 12 circles.",
                    )
                    Hairline()
                    DropdownRow("Item size", sizeName(o.itemSize), ItemSize.entries.map { it to sizeName(it) }, { v -> opts { it.copy(itemSize = v) } })
                    Hairline()
                    DropdownRow(
                        "Item shape", o.itemShape.name.lowercase().replaceFirstChar { it.uppercase() },
                        ItemShape.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { v -> opts { it.copy(itemShape = v) } },
                    )
                    Hairline()
                    DropdownRow(
                        "Future progress", o.futureStyle.name.lowercase().replaceFirstChar { it.uppercase() },
                        FutureStyle.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { v -> opts { it.copy(futureStyle = v) } },
                        helper = "How the items that have not happened yet look.",
                    )
                }
                EventDesign.MINIMAL -> {
                    SwitchRow("Show icon", o.showIcon, { v -> opts { it.copy(showIcon = v) } })
                    if (o.showIcon) {
                        Hairline()
                        SettingRow("Icon") {
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable(role = Role.Button) { iconOpen = true }
                                    .semantics { contentDescription = "Icon: ${EventIcons.get(o.icon).label}. Tap to change." },
                                contentAlignment = Alignment.Center,
                            ) { Icon(EventIcons.get(o.icon).vector(), null) }
                        }
                        Hairline()
                        SettingRow("Icon colour") { ColorDot(Color(o.iconColor), 28.dp, false, "Icon colour. Tap to change.") { colorTarget = 3 } }
                    }
                    Hairline()
                    DropdownRow(
                        "Shape", o.shape.name.lowercase().replaceFirstChar { it.uppercase() },
                        TileShape.entries.map { it to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { v -> opts { it.copy(shape = v) } },
                    )
                    Hairline()
                    DropdownRow("Progress bar size", sizeName(o.barSize), ItemSize.entries.map { it to sizeName(it) }, { v -> opts { it.copy(barSize = v) } })
                    Hairline()
                    SwitchRow("Show progress bar", o.showBar, { v -> opts { it.copy(showBar = v) } })
                }
            }
        }

        DaymarkCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
            SettingRow("Colors") {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ColorDot(Color(o.bg), 30.dp, false, "Background colour. Tap to change.") { colorTarget = 0 }
                    if (o.design != EventDesign.FONTY) ColorDot(Color(o.accent), 30.dp, false, "Accent colour. Tap to change.") { colorTarget = 1 }
                    ColorDot(Color(o.text), 30.dp, false, "Text colour. Tap to change.") { colorTarget = 2 }
                }
            }
            Text("Quick looks", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                EventDefaults.presets.forEach { p ->
                    val on = p.bg == o.bg && p.accent == o.accent && p.text == o.text
                    PresetChip(p.name, p.bg, p.accent, on) { opts { it.copy(bg = p.bg, accent = p.accent, text = p.text) } }
                }
            }
            Hairline()
            val font = EventFonts.all.firstOrNull { it.id == o.fontId }?.label ?: "Inter"
            SettingRow("Font") {
                Row(
                    Modifier.heightIn(min = 48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(role = Role.Button) { fontOpen = true }.semantics { contentDescription = "Font, $font. Tap to change." }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(font, style = MaterialTheme.typography.titleSmall)
                    Icon(Icons.Rounded.UnfoldMore, null, modifier = Modifier.size(18.dp))
                }
            }
            if (o.design != EventDesign.FONTY) {
                Hairline()
                SettingRow("Normal direction", "Which way the ring or items fill up.") {
                    PillToggle(
                        listOf(
                            ToggleItem(true, "Clockwise", icon = Icons.Rounded.RotateRight),
                            ToggleItem(false, "Counter-clockwise", icon = Icons.Rounded.RotateLeft),
                        ),
                        o.clockwise, { v -> opts { it.copy(clockwise = v) } },
                    )
                }
            }
        }

        DaymarkCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
            SwitchRow(
                "Show name on widgets and notifications", o.showNameOutside, { v -> opts { it.copy(showNameOutside = v) } },
                "When off, widgets and notifications say just \"Event\" instead of the name. Daymark's discreet mode only hides habits, so this is the switch for events.",
            )
        }
    }

    if (colorTarget >= 0) {
        val (title, cur) = when (colorTarget) {
            0 -> "Background colour" to o.bg
            1 -> "Accent colour" to o.accent
            2 -> "Text colour" to o.text
            else -> "Icon colour" to o.iconColor
        }
        ColorSheet(title, cur, { c ->
            opts { when (colorTarget) { 0 -> it.copy(bg = c); 1 -> it.copy(accent = c); 2 -> it.copy(text = c); else -> it.copy(iconColor = c) } }
        }, { colorTarget = -1 })
    }
    if (fontOpen) FontSheet(o.fontId, { id -> opts { it.copy(fontId = id) }; fontOpen = false }, { fontOpen = false })
    if (iconOpen) IconSheet(o.icon, { id -> opts { it.copy(icon = id) }; iconOpen = false }, { iconOpen = false })
}

@Composable
private fun PresetChip(name: String, bg: Int, accent: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$name colour look${if (selected) ", selected" else ""}" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Color(bg))
                .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(accent))) }
    }
}
