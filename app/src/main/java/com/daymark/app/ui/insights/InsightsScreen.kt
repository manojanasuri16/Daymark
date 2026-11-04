package com.daymark.app.ui.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.Format
import com.daymark.app.domain.InsightsData
import com.daymark.app.domain.Options
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.EmptyHint
import com.daymark.app.ui.components.GrowingBar
import com.daymark.app.ui.components.SectionHeader
import com.daymark.app.ui.theme.DaymarkTheme
import com.daymark.app.ui.theme.LocalReducedMotion
import com.daymark.app.ui.theme.Motion
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

@Composable
fun InsightsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val insights by vm.insights.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val data = insights

    if (data == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val hasRelapses = stats.relapseCount > 0 ||
        data.monthlyRelapses.any { it.second > 0 } ||
        data.timeOfDay.any { it.second > 0 } ||
        data.moodCounts.any { it.second > 0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Insights",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        item {
            DaymarkCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    SummaryStat(Format.decimalDays(data.averageStreakDays), "Average streak", Modifier.weight(1f))
                    SummaryStat(stats.longestDays.toString(), "Longest (days)", Modifier.weight(1f))
                    SummaryStat(stats.streaks.size.toString(), "Attempts", Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    trendText(data.trend),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { StreakHistoryCard(data) }

        if (!hasRelapses) {
            item {
                DaymarkCard(Modifier.fillMaxWidth()) {
                    EmptyHint(
                        title = "Nothing to analyse yet",
                        body = "If you ever log a slip, patterns will show up here to help you understand your triggers.",
                        icon = Icons.Rounded.Insights,
                    )
                }
            }
        } else {
            item { MonthlyCard(data) }
            item { TriggersCard(data) }
            item { WhenCard(data) }
            item { MoodCard(data) }
            item { SituationsCard(data) }
        }
    }
}

private fun trendText(trend: InsightsData.Trend): String = when (trend) {
    InsightsData.Trend.IMPROVING -> "Your streaks are getting longer. That's real progress."
    InsightsData.Trend.STEADY -> "You're holding steady. Consistency matters."
    InsightsData.Trend.HARDER -> "Recent stretches have been tougher. Be gentle with yourself — every day still counts."
    InsightsData.Trend.NOT_ENOUGH_DATA -> "Keep going — trends appear after a few streaks."
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.5).sp),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun StreakHistoryCard(data: InsightsData) {
    val values = data.streakHistoryDays.takeLast(20)
    val primary = MaterialTheme.colorScheme.primary
    val clean = DaymarkTheme.colors.clean.copy(alpha = 0.55f)
    DaymarkCard(Modifier.fillMaxWidth()) {
        CardTitle("Streak history")
        if (values.isEmpty()) {
            Text("No streaks yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val colors = values.indices.map { if (it == values.lastIndex) primary else clean }
            BarChart(
                values = values.map { it.toFloat() },
                colors = colors,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                averageLine = data.averageStreakDays.toFloat(),
                lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Last ${values.size} streaks, in days. Dashed line is your average (${Format.decimalDays(data.averageStreakDays)}).",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthlyCard(data: InsightsData) {
    DaymarkCard(Modifier.fillMaxWidth()) {
        CardTitle("Slips by month")
        if (data.monthlyRelapses.all { it.second == 0 }) {
            Text(
                "No slips logged in the last 6 months.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val color = DaymarkTheme.colors.relapse.copy(alpha = 0.85f)
            BarChart(
                values = data.monthlyRelapses.map { it.second.toFloat() },
                colors = data.monthlyRelapses.map { color },
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
            Spacer(Modifier.height(6.dp))
            ChartLabels(data.monthlyRelapses.map { it.first.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()) })
            Spacer(Modifier.height(6.dp))
            ChartLabels(data.monthlyRelapses.map { it.second.toString() }, emphasis = true)
        }
    }
}

@Composable
private fun TriggersCard(data: InsightsData) {
    DaymarkCard(Modifier.fillMaxWidth()) {
        CardTitle("Common triggers")
        val top = data.topTriggers.take(6)
        if (top.isEmpty()) {
            EmptyHint("No triggers yet", "Tag triggers when you log a slip to see them here.", Icons.Rounded.Insights)
        } else {
            BarRows(top, DaymarkTheme.colors.accentWarm)
        }
    }
}

@Composable
private fun WhenCard(data: InsightsData) {
    DaymarkCard(Modifier.fillMaxWidth()) {
        CardTitle("When it happens")
        BarRows(data.timeOfDay, DaymarkTheme.colors.accentWarm)
        Spacer(Modifier.height(20.dp))
        Text("By weekday", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        val color = DaymarkTheme.colors.relapse.copy(alpha = 0.85f)
        BarChart(
            values = data.weekdays.map { it.second.toFloat() },
            colors = data.weekdays.map { color },
            modifier = Modifier.fillMaxWidth().height(80.dp),
        )
        Spacer(Modifier.height(6.dp))
        ChartLabels(data.weekdays.map { it.first.getDisplayName(DateTextStyle.NARROW, Locale.getDefault()) })
    }
}

@Composable
private fun MoodCard(data: InsightsData) {
    DaymarkCard(Modifier.fillMaxWidth()) {
        CardTitle("Mood before a slip")
        val counts = data.moodCounts.toMap()
        val max = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Options.moods.forEach { mood ->
                val c = counts[mood.value] ?: 0
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mood.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    GrowingBar(
                        fraction = c.toFloat() / max,
                        color = DaymarkTheme.colors.accentWarm,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        c.toString(),
                        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = (-0.2).sp),
                    )
                }
            }
        }
        data.averageMood?.let { avg ->
            Spacer(Modifier.height(14.dp))
            val nearest = Options.mood(Math.round(avg).toInt().coerceIn(1, 5))
            Text(
                "Average mood: ${"%.1f".format(avg)} / 5" + (nearest?.let { " (${it.label.lowercase()})" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SituationsCard(data: InsightsData) {
    DaymarkCard(Modifier.fillMaxWidth()) {
        CardTitle("Situations")
        val top = data.situations.take(6)
        if (top.isEmpty()) {
            EmptyHint("No situations yet", "Add a situation when you log a slip to see it here.", Icons.Rounded.Insights)
        } else {
            BarRows(top, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BarRows(items: List<Pair<String, Int>>, color: Color) {
    val max = (items.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (label, count) ->
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = (-0.2).sp, fontWeight = FontWeight.SemiBold),
                    )
                }
                Spacer(Modifier.height(6.dp))
                GrowingBar(count.toFloat() / max, color, Modifier.fillMaxWidth().height(8.dp))
            }
        }
    }
}

@Composable
private fun ChartLabels(labels: List<String>, emphasis: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        labels.forEach {
            Text(
                it,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = if (emphasis) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                color = if (emphasis) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Vertical bar chart with rounded tops, spring-in animation and an optional dashed reference line. */
@Composable
private fun BarChart(
    values: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    averageLine: Float? = null,
    lineColor: Color = Color.Gray,
) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    // Grow in once; later data updates redraw in place instead of restarting from zero.
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, Motion.gentle())
    }
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val maxV = maxOf(values.max(), averageLine ?: 0f, 1f)
        val slot = size.width / values.size
        val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        val corner = minOf(barW / 2f, 6.dp.toPx())
        val minH = 3.dp.toPx()
        val p = progress.value
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barW) / 2f
            // faint track
            drawRoundRect(track, Offset(x, 0f), androidx.compose.ui.geometry.Size(barW, size.height), CornerRadius(corner, corner))
            if (v > 0f) {
                val h = (maxOf(v / maxV * size.height, minH) * p).coerceAtLeast(0f)
                if (h > 0.5f) {
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(x, size.height - h, x + barW, size.height),
                                topLeft = CornerRadius(corner, corner),
                                topRight = CornerRadius(corner, corner),
                                bottomRight = CornerRadius.Zero,
                                bottomLeft = CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(path, colors.getOrElse(i) { colors.last() })
                }
            }
        }
        if (averageLine != null && averageLine > 0f) {
            val y = size.height - averageLine / maxV * size.height
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                alpha = p,
            )
        }
    }
}
