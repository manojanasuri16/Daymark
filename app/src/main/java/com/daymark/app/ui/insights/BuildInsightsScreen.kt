package com.daymark.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.BuildInsights
import com.daymark.app.domain.BuildStats
import com.daymark.app.domain.Format
import com.daymark.app.domain.Habit
import com.daymark.app.domain.Messages
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.EmptyHint
import com.daymark.app.ui.components.amountWithUnit
import com.daymark.app.ui.theme.DaymarkTheme
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

private fun pct(rate: Double, due: Int) = if (due == 0) "—" else "${(rate * 100).roundToInt()}%"

@Composable
internal fun BuildInsightsScreen(vm: MainViewModel, habit: Habit, contentPadding: PaddingValues) {
    val build by vm.buildStats.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val b = build
    if (b == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val today = LocalDate.now()
    val weekday = remember(b.startDate, habit.scheduleDays, habit.goalTarget, completions) {
        BuildInsights.weekdayStats(b.startDate, today, habit.scheduleDays, habit.goalTarget, completions)
    }
    val best = remember(weekday) { BuildInsights.bestWeekday(weekday) }
    val runs = remember(b.startDate, habit.scheduleDays, habit.goalTarget, completions) {
        BuildInsights.streakRuns(b.startDate, today, habit.scheduleDays, habit.goalTarget, completions)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) {
                Text("Insights", style = MaterialTheme.typography.headlineLarge)
                Text("${habit.emoji}  ${habit.displayName}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (b.dueDays == 0 && b.totalDone == 0) {
            item {
                DaymarkCard(Modifier.fillMaxWidth()) {
                    EmptyHint(
                        "Nothing to analyse yet",
                        "Log a few days and your patterns will show up here.",
                        Icons.Rounded.Insights,
                    )
                }
            }
            return@LazyColumn
        }
        item {
            DaymarkCard(Modifier.fillMaxWidth()) {
                Text("Completion rate", style = MaterialTheme.typography.titleMedium)
                Text("Share of scheduled days you completed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat(pct(b.completionRate, b.dueDays), "All time", Modifier.weight(1f))
                    Stat(pct(b.last30Rate, b.dueDays), "Last 30 days", Modifier.weight(1f))
                    Stat(pct(b.last7Rate, b.dueDays), "Last 7 days", Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    trend(b),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { StreaksCard(b, runs) }
        item { WeeklyCard(b) }
        item { WeekdayCard(weekday, best) }
        item { TotalsCard(habit, b) }
    }
}

private fun trend(b: BuildStats): String = when {
    b.dueDays < 7 -> "Keep going. Patterns appear after a week or two."
    b.last30Rate >= b.completionRate + 0.05 -> "You're on an upswing lately. That's real progress."
    b.last30Rate <= b.completionRate - 0.15 -> "Recent weeks have been lighter. ${Messages.BUILD_MISS_NOTE}"
    else -> "You're holding steady. Consistency matters."
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.5).sp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun StreaksCard(b: BuildStats, runs: List<Int>) {
    val values = runs.takeLast(20)
    val primary = MaterialTheme.colorScheme.primary
    val clean = DaymarkTheme.colors.clean.copy(alpha = 0.55f)
    DaymarkCard(Modifier.fillMaxWidth()) {
        CardTitle("Streaks")
        Row(Modifier.fillMaxWidth()) {
            Stat(b.currentStreak.toString(), "Current (days)", Modifier.weight(1f))
            Stat(b.longestStreak.toString(), "Longest (days)", Modifier.weight(1f))
            Stat(runs.size.toString(), if (runs.size == 1) "Streak" else "Streaks", Modifier.weight(1f))
        }
        if (values.size > 1) {
            Spacer(Modifier.height(16.dp))
            BarChart(
                values = values.map { it.toFloat() },
                colors = values.indices.map { if (it == values.lastIndex && b.currentStreak > 0) primary else clean },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your last ${values.size} streaks, in scheduled days.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeeklyCard(b: BuildStats) {
    val primary = MaterialTheme.colorScheme.primary
    val clean = DaymarkTheme.colors.clean.copy(alpha = 0.55f)
    DaymarkCard(Modifier.fillMaxWidth()) {
        CardTitle("Weekly completion", "Last 8 weeks")
        BarChart(
            values = b.weekly.map { it.second.toFloat() },
            colors = b.weekly.indices.map { if (it == b.weekly.lastIndex) primary else clean },
            modifier = Modifier.fillMaxWidth().height(110.dp),
        )
        Spacer(Modifier.height(6.dp))
        ChartLabels(b.weekly.map { it.first.dayOfMonth.toString() })
    }
}

@Composable
private fun WeekdayCard(stats: List<com.daymark.app.domain.WeekdayStat>, best: java.time.DayOfWeek?) {
    DaymarkCard(Modifier.fillMaxWidth()) {
        CardTitle("By weekday")
        val color = DaymarkTheme.colors.clean.copy(alpha = 0.8f)
        BarChart(
            values = stats.map { it.rate.toFloat() },
            colors = stats.map { color },
            modifier = Modifier.fillMaxWidth().height(80.dp),
        )
        Spacer(Modifier.height(6.dp))
        ChartLabels(stats.map { it.day.getDisplayName(DateTextStyle.NARROW, Locale.getDefault()) })
        Spacer(Modifier.height(10.dp))
        Text(
            best?.let { "${it.getDisplayName(DateTextStyle.FULL, Locale.getDefault())} is your strongest day." }
                ?: "Your strongest day will show up as you log more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TotalsCard(habit: Habit, b: BuildStats) {
    DaymarkCard(Modifier.fillMaxWidth()) {
        CardTitle("Totals")
        Row(Modifier.fillMaxWidth()) {
            Stat(b.totalDone.toString(), "Days done", Modifier.weight(1f))
            Stat(b.missedDays.toString(), if (b.missedDays == 1) "Missed day" else "Missed days", Modifier.weight(1f))
            if (habit.hasGoal) Stat(amountWithUnit(b.averageAmount, habit.goalUnit), "Average per active day", Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
        }
        if (habit.hasGoal) {
            Spacer(Modifier.height(14.dp))
            Text(
                "${amountWithUnit(b.totalAmount, habit.goalUnit)} in total over ${Format.days(java.time.temporal.ChronoUnit.DAYS.between(b.startDate, LocalDate.now()) + 1)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(Messages.BUILD_MISS_NOTE, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
