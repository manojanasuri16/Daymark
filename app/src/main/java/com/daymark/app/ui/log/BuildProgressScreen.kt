package com.daymark.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.daymark.app.ui.components.amountWithUnit
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.theme.DaymarkTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The "log" destination for build habits: add to today, and review or fix recent days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildProgressScreen(vm: MainViewModel, onClose: () -> Unit) {
    val habit by vm.selectedHabit.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val build by vm.buildStats.collectAsStateWithLifecycle()
    val h = habit
    val b = build
    val haptics = rememberHaptics()
    var editingDay by remember { mutableStateOf<LocalDate?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Progress")
                    h?.let {
                        Text("${it.emoji}  ${it.displayName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (h == null || b == null || !h.isBuild) return@Column

        val today = LocalDate.now()
        val amounts = remember(completions) { completions.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.amount } } }
        val mask = Schedule.normalize(h.scheduleDays)
        val step = if (h.goalStep > 0) h.goalStep else 1.0
        val days = remember(b.startDate, today) {
            val from = maxOf(b.startDate, today.minusDays(13))
            generateSequence(today) { it.minusDays(1) }.takeWhile { !it.isBefore(from) }.toList()
        }

        LazyColumn(
            Modifier.weight(1f).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("today") {
                DaymarkCard(Modifier.fillMaxWidth()) {
                    Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    if (h.hasGoal) {
                        Text(
                            "${amountWithUnit(b.todayAmount, h.goalUnit)} of ${amountWithUnit(b.todayTarget, h.goalUnit)}",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    } else {
                        Text(if (b.todayDone) "Done" else "Not done yet", style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                    if (h.hasGoal) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(
                                onClick = { haptics.tick(); vm.logProgress(h, -minOf(step, b.todayAmount)) },
                                enabled = b.todayAmount > 0,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(52.dp).semantics { contentDescription = "Remove ${amountWithUnit(step, h.goalUnit)}" },
                            ) { Icon(Icons.Rounded.Remove, null) }
                            Button(
                                onClick = {
                                    if (!b.todayDone && b.todayAmount + step >= b.todayTarget - 1e-9) haptics.confirm() else haptics.tick()
                                    vm.logProgress(h, step)
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f).height(52.dp),
                            ) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("+${amountWithUnit(step, h.goalUnit)}")
                            }
                            FilledTonalButton(
                                onClick = { haptics.tick(); editingDay = today },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(52.dp).semantics { contentDescription = "Enter an exact amount" },
                            ) { Icon(Icons.Rounded.Edit, null) }
                        }
                    } else {
                        Button(
                            onClick = { if (b.todayDone) haptics.tick() else haptics.confirm(); vm.toggleDone(h) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Rounded.Check, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (b.todayDone) "Done · tap to undo" else "Mark done")
                        }
                    }
                    if (!b.todayScheduled) {
                        Spacer(Modifier.height(10.dp))
                        Text("Today is a rest day, so anything you log is a bonus.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item("recentHeader") {
                Column(Modifier.padding(start = 4.dp, top = 8.dp)) {
                    Text("Recent days", style = MaterialTheme.typography.titleMedium)
                    Text("Tap a day to fix it. ${Messages.BUILD_MISS_NOTE}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(days, key = { it.toEpochDay() }) { day ->
                val amount = amounts[day] ?: 0.0
                val status = BuildEngine.status(day, mask, h.goalTarget, amounts, b.startDate, today)
                DayRow(
                    day = day,
                    today = today,
                    status = status,
                    detail = when {
                        h.hasGoal && amount > 0 -> amountWithUnit(amount, h.goalUnit)
                        status == BuildDay.DONE -> "Done"
                        status == BuildDay.REST -> "Rest day"
                        status == BuildDay.PENDING -> "Open"
                        else -> "—"
                    },
                    onClick = {
                        haptics.tick()
                        if (h.hasGoal) editingDay = day else vm.toggleDone(h, day)
                    },
                )
            }
        }

        editingDay?.let { day ->
            AmountDialog(
                title = if (day == today) "Today's amount" else day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                unit = h.goalUnit,
                initial = amounts[day] ?: 0.0,
                onDismiss = { editingDay = null },
                onConfirm = { editingDay = null; vm.setProgress(h, it, day) },
            )
        }
    }
}

@Composable
private fun DayRow(day: LocalDate, today: LocalDate, status: BuildDay, detail: String, onClick: () -> Unit) {
    val colors = DaymarkTheme.colors
    val dot = when (status) {
        BuildDay.DONE -> colors.clean
        BuildDay.PARTIAL -> colors.partial
        BuildDay.MISSED -> colors.relapse.copy(alpha = 0.6f)
        BuildDay.PENDING -> MaterialTheme.colorScheme.primary
        else -> colors.untracked
    }
    DaymarkCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = dot, shape = CircleShape, modifier = Modifier.size(10.dp)) {}
            Spacer(Modifier.width(12.dp))
            Text(
                if (day == today) "Today" else if (day == today.minusDays(1)) "Yesterday"
                else day.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
