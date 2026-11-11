package com.daymark.app.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.DayStatus
import com.daymark.app.domain.Options
import com.daymark.app.domain.JournalEntry
import com.daymark.app.domain.NonSlipNote
import com.daymark.app.domain.RelapseRecord
import com.daymark.app.domain.UrgeRecord
import com.daymark.app.domain.StreakEngine
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.theme.DaymarkTheme
import com.daymark.app.ui.theme.Motion
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun CalendarScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onLogForDay: (LocalDate) -> Unit,
    onEdit: (RelapseRecord) -> Unit,
    onUrgeForDay: (LocalDate) -> Unit = {},
    onEditUrge: (UrgeRecord) -> Unit = {},
    onJournalForDay: (LocalDate) -> Unit = {},
    onEditJournal: (JournalEntry) -> Unit = {},
    onEditNote: (NonSlipNote) -> Unit = {},
) {
    val habit by vm.selectedHabit.collectAsStateWithLifecycle()
    habit?.takeIf { it.isBuild }?.let { h ->
        BuildCalendarScreen(vm, contentPadding, h, onJournalForDay)
        return
    }
    val relapses by vm.relapses.collectAsStateWithLifecycle()
    val urges by vm.urges.collectAsStateWithLifecycle()
    val journal by vm.journal.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now()
    val journeyStart = habit?.startAt ?: System.currentTimeMillis()
    val slipTimes = remember(relapses, habit) { habit?.slipTimes(relapses.map { it.timestamp }, zone) ?: relapses.map { it.timestamp } }
    val earliest = slipTimes.minOrNull()
    val startDate = StreakEngine.toDate(earliest?.let { minOf(it, journeyStart) } ?: journeyStart, zone)
    val noteDays = remember(notes) { notes.map { StreakEngine.toDate(it.timestamp, zone) }.toSet() }
    val relapseDates = remember(slipTimes) { StreakEngine.relapseDates(slipTimes, zone) }

    val firstMonth = YearMonth.from(startDate).let { if (it.isAfter(YearMonth.from(today))) YearMonth.from(today) else it }
    val thisMonth = YearMonth.from(today)
    val pageCount = (ChronoUnit.MONTHS.between(firstMonth, thisMonth).toInt() + 1).coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = pageCount - 1) { pageCount }
    val scope = rememberCoroutineScope()
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    val month = firstMonth.plusMonths(pager.currentPage.toLong())

    val statusOf: (LocalDate) -> DayStatus = { d ->
        StreakEngine.dayStatus(d, journeyStart, relapseDates, earliest, today, zone)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("title") {
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text("Calendar", style = MaterialTheme.typography.headlineLarge)
                habit?.let {
                    Text("${it.emoji}  ${it.displayName}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("month") {
            DaymarkCard(contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(month, transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard()) },
                        modifier = Modifier.weight(1f).padding(start = 4.dp), label = "monthTitle") { m ->
                        Text(
                            "${m.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${m.year}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month")
                    }
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < pageCount - 1) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month")
                    }
                }
                Spacer(Modifier.height(8.dp))
                WeekdayHeader()
                Spacer(Modifier.height(4.dp))
                HorizontalPager(state = pager, beyondViewportPageCount = 1, verticalAlignment = Alignment.Top) { page ->
                    MonthGrid(
                        month = firstMonth.plusMonths(page.toLong()),
                        today = today,
                        selected = selected,
                        statusOf = statusOf,
                        noteDays = noteDays,
                        onSelect = { selectedEpochDay = it.toEpochDay() },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Legend()
            }
        }
        item("summary") { MonthSummary(month, today, statusOf) }
        item("dayHeader") {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val dayEntries = relapses.filter { StreakEngine.toDate(it.timestamp, zone) == selected }.sortedBy { it.timestamp }
        val status = statusOf(selected)
        if (dayEntries.isEmpty()) {
            item("dayEmpty") {
                DaymarkCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Eco, null,
                            tint = if (status == DayStatus.CLEAN) DaymarkTheme.colors.clean else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when (status) {
                                DayStatus.CLEAN -> if (selected == today) "Clean so far today. Keep going." else "A clean day. Well done."
                                DayStatus.UNTRACKED -> if (selected.isAfter(today)) "This day hasn't happened yet." else "Before your journey began."
                                DayStatus.RELAPSE -> ""
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        } else {
            items(dayEntries, key = { it.id }) { r -> EntryCard(r, zone) { onEdit(r) } }
        }
        val dayUrges = urges.filter { StreakEngine.toDate(it.timestamp, zone) == selected }.sortedBy { it.timestamp }
        val dayJournal = journal.filter { StreakEngine.toDate(it.timestamp, zone) == selected }.sortedBy { it.timestamp }
        items(dayUrges, key = { "u${it.id}" }) { u -> UrgeCard(u, zone) { onEditUrge(u) } }
        val dayNotes = notes.filter { StreakEngine.toDate(it.timestamp, zone) == selected }.sortedBy { it.timestamp }
        items(dayNotes, key = { "n${it.id}" }) { n -> NoteCard(n, zone) { onEditNote(n) } }
        items(dayJournal, key = { "j${it.id}" }) { j -> JournalCard(j, zone) { onEditJournal(j) } }
        if (!selected.isAfter(today)) {
            item("logDay") {
                FilledTonalButton(
                    onClick = { onLogForDay(selected) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (habit?.isReduce == true) "Log a use on this day" else "Log a slip on this day")
                }
            }
            item("moreDay") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onUrgeForDay(selected) },
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp),
                    ) { Text("Urge ridden out") }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onJournalForDay(selected) },
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp),
                    ) { Text("Journal") }
                }
            }
        }
    }
}

internal fun weekStart(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

@Composable
internal fun WeekdayHeader() {
    val first = weekStart()
    Row(Modifier.fillMaxWidth()) {
        (0 until 7).forEach { i ->
            val d = first.plus(i.toLong())
            Text(
                d.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    statusOf: (LocalDate) -> DayStatus,
    noteDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val offset = ((first.dayOfWeek.value - weekStart().value) + 7) % 7
    val cells = offset + month.lengthOfMonth()
    val rows = 6 // fixed height so paging between months never jumps
    Column {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    val day = index - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (index in offset until cells) {
                            val date = month.atDay(day)
                            DayCell(date, statusOf(date), date == today, date == selected, date in noteDays) { onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, status: DayStatus, isToday: Boolean, isSelected: Boolean, hasNote: Boolean, onClick: () -> Unit) {
    val colors = DaymarkTheme.colors
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val fill = when (status) {
        DayStatus.CLEAN -> colors.clean
        DayStatus.RELAPSE -> colors.relapse
        DayStatus.UNTRACKED -> colors.untracked
    }
    val bg by animateColorAsState(fill.copy(alpha = if (status == DayStatus.UNTRACKED) 0.6f else 0.9f), Motion.standard(), label = "cell")
    val textColor = if (status == DayStatus.UNTRACKED) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
    Box(
        Modifier
            .fillMaxSize()
            .pressScale(source, 0.9f)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(source, null) { haptics.tick(); onClick() }
            .padding(if (isSelected) 4.dp else 0.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = bg, shape = CircleShape, modifier = Modifier.fillMaxSize()) {}
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium),
            color = textColor,
        )
        if (isToday) {
            Surface(
                color = textColor, shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp),
            ) {}
        }
        if (hasNote) {
            Surface(
                color = textColor.copy(alpha = 0.85f), shape = CircleShape,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(4.dp),
            ) {}
        }
    }
}

@Composable
private fun Legend() {
    val c = DaymarkTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LegendItem(c.clean, "Clean")
        LegendItem(c.relapse, "Slip")
        LegendItem(c.untracked, "Untracked")
    }
}

@Composable
internal fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthSummary(month: YearMonth, today: LocalDate, statusOf: (LocalDate) -> DayStatus) {
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { !it.isAfter(today) }
    val clean = days.count { statusOf(it) == DayStatus.CLEAN }
    val slips = days.count { statusOf(it) == DayStatus.RELAPSE }
    val tracked = clean + slips
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryTile("$clean", "clean days", DaymarkTheme.colors.clean, Modifier.weight(1f))
        SummaryTile("$slips", if (slips == 1) "slip" else "slips", DaymarkTheme.colors.relapse, Modifier.weight(1f))
        SummaryTile(if (tracked == 0) "—" else "${clean * 100 / tracked}%", "of tracked days", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(value: String, label: String, color: Color, modifier: Modifier) {
    DaymarkCard(modifier = modifier, contentPadding = 14.dp) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntryCard(r: RelapseRecord, zone: ZoneId, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(r.timestamp).atZone(zone).toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val mood = Options.mood(r.mood)
    DaymarkCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = DaymarkTheme.colors.relapse.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(mood?.emoji ?: "•", style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(time, r.situation.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (r.triggers.isNotEmpty()) {
                    Text(r.triggers.joinToString(", "), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (r.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(r.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 4,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UrgeCard(u: UrgeRecord, zone: ZoneId, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(u.timestamp).atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    DaymarkCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = DaymarkTheme.colors.clean.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("🛡️", style = MaterialTheme.typography.titleLarge) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("$time · Urge ridden out", style = MaterialTheme.typography.titleSmall)
                if (u.triggers.isNotEmpty()) {
                    Text(u.triggers.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (u.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(u.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NoteCard(n: NonSlipNote, zone: ZoneId, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(n.timestamp).atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    DaymarkCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("📝", style = MaterialTheme.typography.titleLarge) }
            }
            Spacer(Modifier.width(14.dp))
            Text("$time · Not a slip", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        if (n.notes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(n.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun JournalCard(j: JournalEntry, zone: ZoneId, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(j.timestamp).atZone(zone).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    DaymarkCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(Options.mood(j.mood)?.emoji ?: "📝", style = MaterialTheme.typography.titleLarge) }
            }
            Spacer(Modifier.width(14.dp))
            Text(if (j.photos.isEmpty()) "$time · Journal" else "$time · Journal · ${j.photos.size} ${if (j.photos.size == 1) "photo" else "photos"}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        if (j.text.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(j.text, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (j.photos.isNotEmpty()) {
            var viewing by remember { mutableStateOf<String?>(null) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                j.photos.forEach { com.daymark.app.ui.components.PhotoThumb(it, 64.dp, onClick = { viewing = it }) }
            }
            viewing?.let { com.daymark.app.ui.components.PhotoViewer(it) { viewing = null } }
        }
    }
}
