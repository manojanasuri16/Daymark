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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.BuildDay
import com.daymark.app.domain.BuildEngine
import com.daymark.app.domain.Habit
import com.daymark.app.domain.Messages
import com.daymark.app.domain.Schedule
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.AmountDialog
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.GrowingBar
import com.daymark.app.ui.components.amountWithUnit
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.theme.DaymarkTheme
import com.daymark.app.ui.theme.Motion
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
internal fun BuildCalendarScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    habit: Habit,
    onJournalForDay: (LocalDate) -> Unit,
) {
    val completions by vm.completions.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now()
    val mask = Schedule.normalize(habit.scheduleDays)
    val amounts = remember(completions) { completions.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.amount } } }
    val startDate = remember(amounts, habit.startAt) { BuildEngine.effectiveStart(habit.startAt, amounts, zone) }
    val statusOf: (LocalDate) -> BuildDay = { d -> BuildEngine.status(d, mask, habit.goalTarget, amounts, startDate, today) }

    val firstMonth = YearMonth.from(startDate).let { if (it.isAfter(YearMonth.from(today))) YearMonth.from(today) else it }
    val pageCount = (ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(today)).toInt() + 1).coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = pageCount - 1) { pageCount }
    val scope = rememberCoroutineScope()
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    val month = firstMonth.plusMonths(pager.currentPage.toLong())
    val haptics = rememberHaptics()
    var editing by remember { mutableStateOf(false) }

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
                Text("${habit.emoji}  ${habit.displayName}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item("month") {
            DaymarkCard(contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedContent(month, transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard()) },
                        modifier = Modifier.weight(1f).padding(start = 4.dp), label = "monthTitle") { m ->
                        Text("${m.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${m.year}", style = MaterialTheme.typography.titleLarge)
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
                    BuildMonthGrid(
                        month = firstMonth.plusMonths(page.toLong()),
                        today = today,
                        selected = selected,
                        statusOf = statusOf,
                        onSelect = { selectedEpochDay = it.toEpochDay() },
                    )
                }
                Spacer(Modifier.height(12.dp))
                BuildLegend()
            }
        }
        item("summary") { BuildMonthSummary(month, mask, statusOf) }
        item("dayHeader") {
            Text(
                selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        item("dayCard") {
            val status = statusOf(selected)
            val amount = amounts[selected] ?: 0.0
            val editable = !selected.isAfter(today) && !selected.isBefore(startDate)
            DaymarkCard(Modifier.fillMaxWidth()) {
                Text(
                    when (status) {
                        BuildDay.DONE -> "Done. Well done."
                        BuildDay.PARTIAL -> "Partly done"
                        BuildDay.MISSED -> "Not logged"
                        BuildDay.PENDING -> "Still open today"
                        BuildDay.REST -> "Rest day"
                        BuildDay.UNTRACKED -> if (selected.isAfter(today)) "This day hasn't happened yet." else "Before you began."
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (habit.hasGoal && status != BuildDay.UNTRACKED) {
                    Text(
                        "${amountWithUnit(amount, habit.goalUnit)} of ${amountWithUnit(habit.goalTarget, habit.goalUnit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status == BuildDay.MISSED) {
                        Text(Messages.BUILD_MISS_NOTE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (status == BuildDay.MISSED) {
                    Text(Messages.BUILD_MISS_NOTE, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (editable) {
                    Spacer(Modifier.height(14.dp))
                    if (habit.hasGoal) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(
                                onClick = { haptics.confirm(); vm.setProgress(habit, BuildEngine.effectiveTarget(habit.goalTarget), selected) },
                                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp),
                                enabled = status != BuildDay.DONE,
                            ) {
                                Icon(Icons.Rounded.Check, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Goal reached")
                            }
                            OutlinedButton(
                                onClick = { haptics.tick(); editing = true },
                                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp),
                            ) {
                                Icon(Icons.Rounded.Edit, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Set amount")
                            }
                        }
                    } else {
                        val done = status == BuildDay.DONE
                        FilledTonalButton(
                            onClick = { if (done) haptics.tick() else haptics.confirm(); vm.toggleDone(habit, selected) },
                            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Check, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (done) "Mark as not done" else "Mark as done")
                        }
                    }
                }
            }
        }
        if (!selected.isAfter(today)) {
            item("journalDay") {
                OutlinedButton(
                    onClick = { onJournalForDay(selected) },
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp),
                ) { Text("Journal for this day") }
            }
        }
    }

    if (editing) {
        AmountDialog(
            title = selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            unit = habit.goalUnit,
            initial = amounts[selected] ?: 0.0,
            onDismiss = { editing = false },
            onConfirm = { editing = false; vm.setProgress(habit, it, selected) },
        )
    }
}

@Composable
private fun BuildMonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    statusOf: (LocalDate) -> BuildDay,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    val offset = ((first.dayOfWeek.value - weekStart().value) + 7) % 7
    val cells = offset + month.lengthOfMonth()
    Column {
        for (row in 0 until 6) { // fixed height so paging between months never jumps
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (index in offset until cells) {
                            val date = month.atDay(index - offset + 1)
                            BuildDayCell(date, statusOf(date), date == today, date == selected) { onSelect(date) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildDayCell(date: LocalDate, status: BuildDay, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val colors = DaymarkTheme.colors
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val fill = when (status) {
        BuildDay.DONE -> colors.clean.copy(alpha = 0.9f)
        BuildDay.PARTIAL -> colors.partial.copy(alpha = 0.4f)
        BuildDay.MISSED -> colors.relapse.copy(alpha = 0.18f)
        BuildDay.REST -> colors.untracked.copy(alpha = 0.35f)
        BuildDay.PENDING -> Color.Transparent
        BuildDay.UNTRACKED -> colors.untracked.copy(alpha = 0.6f)
    }
    val bg by animateColorAsState(fill, Motion.standard(), label = "buildCell")
    val textColor = when (status) {
        BuildDay.DONE -> Color.White
        BuildDay.PARTIAL, BuildDay.PENDING -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val pendingRing = if (status == BuildDay.PENDING) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
    val label = status.name.lowercase().replaceFirstChar(Char::uppercase)
    Box(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "${date.dayOfMonth}, $label" }
            .pressScale(source, 0.9f)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else pendingRing)
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
    }
}

@Composable
private fun BuildLegend() {
    val c = DaymarkTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LegendItem(c.clean, "Done")
        LegendItem(c.partial, "Partly")
        LegendItem(c.relapse.copy(alpha = 0.5f), "Missed")
        LegendItem(c.untracked, "Rest")
    }
}

@Composable
private fun BuildMonthSummary(month: YearMonth, mask: Int, statusOf: (LocalDate) -> BuildDay) {
    val today = LocalDate.now()
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { !it.isAfter(today) }
    // Scheduled days that have come due, plus today if already done.
    val scheduled = days.filter { d ->
        val s = statusOf(d)
        Schedule.isScheduled(mask, d) && (s == BuildDay.DONE || s == BuildDay.PARTIAL || s == BuildDay.MISSED)
    }
    val done = scheduled.count { statusOf(it) == BuildDay.DONE }
    DaymarkCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text(
            if (scheduled.isEmpty()) "Nothing scheduled yet this month" else "$done of ${scheduled.size} scheduled ${if (scheduled.size == 1) "day" else "days"} done",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(10.dp))
        GrowingBar(
            fraction = if (scheduled.isEmpty()) 0f else done.toFloat() / scheduled.size,
            color = DaymarkTheme.colors.clean,
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
    }
}
