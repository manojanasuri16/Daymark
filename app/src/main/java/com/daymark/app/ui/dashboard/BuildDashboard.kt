package com.daymark.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daymark.app.domain.BuildEngine
import com.daymark.app.domain.BuildStats
import com.daymark.app.domain.Format
import com.daymark.app.domain.Habit
import com.daymark.app.domain.Messages
import com.daymark.app.domain.Milestone
import com.daymark.app.domain.Milestones
import com.daymark.app.domain.StreakStats
import com.daymark.app.ui.HabitSummary
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.AmountDialog
import com.daymark.app.ui.components.AnimatedText
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.ProgressRing
import com.daymark.app.ui.components.SectionHeader
import com.daymark.app.ui.components.StatTile
import com.daymark.app.ui.components.amountWithUnit
import com.daymark.app.ui.components.formatAmount
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.insights.BarChart
import com.daymark.app.ui.insights.ChartLabels
import com.daymark.app.ui.theme.DaymarkTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

private val BuildHeroNumber = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).sp, lineHeight = 66.sp)

private fun pct(rate: Double, due: Int) = if (due == 0) "—" else "${(rate * 100).roundToInt()}%"

/** Everything the dashboard shows when the selected tracker is a build habit. */
fun LazyListScope.buildDashboardItems(
    vm: MainViewModel,
    habit: Habit,
    build: BuildStats,
    stats: StreakStats,
    overview: List<HabitSummary>,
    today: LocalDate,
    onJournal: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenHistory: () -> Unit,
    onShare: (Milestone) -> Unit,
    showTip: Boolean = false,
    onAddToHome: () -> Unit = {},
) {
    val builders = overview.filter { it.build != null }
    if (builders.size >= 2) {
        item("bToday") { TodayOverviewCard(builders, habit.id, vm) }
    }
    item("bHero") {
        BuildHero(
            habit = habit,
            b = build,
            seed = today.toEpochDay().toInt() + habit.id.toInt(),
            onAdd = { vm.logProgress(habit, it) },
            onSetAmount = { vm.setProgress(habit, it) },
            onToggle = { vm.toggleDone(habit) },
            showTip = showTip,
            onAddToHome = onAddToHome,
        )
    }
    item("bCelebrate") { MilestoneBanner(stats, onShare) }
    item("bStats1") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                value = Format.days(build.currentStreak.toLong()), label = "Current streak",
                icon = Icons.Rounded.LocalFireDepartment, tint = DaymarkTheme.colors.accentWarm, modifier = Modifier.weight(1f),
            )
            StatTile(
                value = Format.days(build.longestStreak.toLong()),
                label = if (build.longestIsCurrent && build.currentStreak > 0) "Best streak · now!" else "Longest streak",
                icon = Icons.Rounded.EmojiEvents, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
            )
        }
    }
    item("bStats2") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                value = pct(build.completionRate, build.dueDays),
                label = "Completion · 7d ${pct(build.last7Rate, build.dueDays)} · 30d ${pct(build.last30Rate, build.dueDays)}",
                icon = Icons.Rounded.Percent, tint = DaymarkTheme.colors.clean, modifier = Modifier.weight(1f),
            )
            StatTile(
                value = build.missedDays.toString(),
                label = if (build.missedDays == 1) "Missed day" else "Missed days",
                icon = Icons.Rounded.CalendarMonth, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
        }
    }
    item("bStats3") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                value = build.totalDone.toString(), label = "Total done",
                icon = Icons.Rounded.Eco, tint = DaymarkTheme.colors.clean, modifier = Modifier.weight(1f),
            )
            if (habit.hasGoal) {
                StatTile(
                    value = amountWithUnit(build.averageAmount, habit.goalUnit), label = "Average per active day",
                    icon = Icons.Rounded.TrendingUp, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
    item("bWeekly") { WeeklyBarsCard(build) }
    item("bQuote") {
        DaymarkCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.FormatQuote, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (build.missedDays > 0 && build.currentStreak == 0) Messages.BUILD_MISS_NOTE else Messages.buildQuote(today),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
    item("bAchHeader") {
        SectionHeader("Achievements") {
            Text(
                "${Milestones.earned(stats.longestDays).size} of ${Milestones.all.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item("bAch") { AchievementsRow(stats) }
    item("bActions") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(
                Icons.Rounded.EditNote, "Journal", "How's today?",
                MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f), onJournal,
            )
            ActionCard(
                Icons.Rounded.CalendarMonth, "Calendar", "Fix a forgotten day",
                MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
                Modifier.weight(1f), onOpenCalendar,
            )
        }
    }
    item("bHistory") {
        DaymarkCard(onClick = onOpenHistory, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Progress history", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Review recent days and adjust any amount.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BuildHero(
    habit: Habit,
    b: BuildStats,
    seed: Int,
    onAdd: (Double) -> Unit,
    onSetAmount: (Double) -> Unit,
    onToggle: () -> Unit,
    showTip: Boolean,
    onAddToHome: () -> Unit,
) {
    val colors = DaymarkTheme.colors
    val haptics = rememberHaptics()
    var showAnyway by rememberSaveable(habit.id) { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val restDay = !b.todayScheduled && !b.todayDone && b.todayAmount <= 0.0 && !showAnyway
    val hasGoal = habit.hasGoal
    val step = if (habit.goalStep > 0) habit.goalStep else 1.0
    val progress = if (hasGoal) b.todayProgress else if (b.todayDone) 1f else 0f
    val remaining = amountWithUnit((b.todayTarget - b.todayAmount).coerceAtLeast(0.0), habit.goalUnit)
    val stateLine = Messages.buildState(b.todayDone, hasGoal, b.todayAmount > 0, restDay, remaining, seed)
    val summary = if (hasGoal) "${amountWithUnit(b.todayAmount, habit.goalUnit)} of ${amountWithUnit(b.todayTarget, habit.goalUnit)} today"
    else if (b.todayDone) "Done today" else "Not done yet today"

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(colors.heroGradientTop, colors.heroGradientBottom)))
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        HomeHint(showTip, Modifier.align(Alignment.BottomCenter).offset(y = 26.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val ringMod = Modifier.size(224.dp).semantics { contentDescription = summary }
            val ringSrc = remember { MutableInteractionSource() }
            val canToggle = !hasGoal && !restDay
            Box(
                ringMod.pressScale(ringSrc, 0.98f).clip(CircleShape).combinedClickable(
                    interactionSource = ringSrc, indication = null,
                    enabled = true,
                    onClick = {
                        if (canToggle) {
                            if (b.todayDone) haptics.tick() else haptics.confirm()
                            onToggle()
                        }
                    },
                    onLongClickLabel = "Add to home screen",
                    onLongClick = { haptics.heavy(); onAddToHome() },
                ),
                contentAlignment = Alignment.Center,
            ) {
                ProgressRing(
                    progress = progress,
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    color = Color.White.copy(alpha = 0.92f),
                )
                when {
                    restDay -> Icon(Icons.Rounded.Spa, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(72.dp))
                    hasGoal -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedText(formatAmount(b.todayAmount), BuildHeroNumber, color = Color.White)
                        Text(
                            "of ${amountWithUnit(b.todayTarget, habit.goalUnit)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    else -> Icon(
                        Icons.Rounded.Check, null,
                        tint = Color.White.copy(alpha = if (b.todayDone) 0.95f else 0.4f), modifier = Modifier.size(96.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            AnimatedText(stateLine, MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(16.dp))

            if (restDay) {
                TextButton(onClick = { showAnyway = true }) {
                    Text("Log anyway", color = Color.White.copy(alpha = 0.9f))
                }
            } else if (hasGoal) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroCircleButton(Icons.Rounded.Remove, "Remove ${amountWithUnit(step, habit.goalUnit)}", enabled = b.todayAmount > 0) {
                        haptics.tick(); onAdd(-minOf(step, b.todayAmount))
                    }
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White,
                    ) {
                        val src = remember { MutableInteractionSource() }
                        Row(
                            Modifier
                                .pressScale(src, 0.96f)
                                .clickable(src, null) {
                                    val reaching = !b.todayDone && b.todayAmount + step >= b.todayTarget - 1e-9
                                    if (reaching) haptics.confirm() else haptics.tick()
                                    onAdd(step)
                                }
                                .semantics { contentDescription = "Add ${amountWithUnit(step, habit.goalUnit)}" }
                                .padding(horizontal = 26.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = colors.heroGradientBottom)
                            Spacer(Modifier.width(6.dp))
                            Text("+${amountWithUnit(step, habit.goalUnit)}", style = MaterialTheme.typography.titleMedium, color = colors.heroGradientBottom)
                        }
                    }
                    HeroCircleButton(Icons.Rounded.Edit, "Enter an exact amount") { haptics.tick(); editing = true }
                }
            } else {
                val src = remember { MutableInteractionSource() }
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (b.todayDone) Color.White.copy(alpha = 0.16f) else Color.White,
                    modifier = Modifier
                        .pressScale(src, 0.96f)
                        .clickable(src, null) {
                            if (b.todayDone) haptics.tick() else haptics.confirm()
                            onToggle()
                        },
                ) {
                    Text(
                        if (b.todayDone) "Done · tap to undo" else "Mark done",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (b.todayDone) Color.White else colors.heroGradientBottom,
                        modifier = Modifier.padding(horizontal = 30.dp, vertical = 16.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                if (b.currentStreak == 1) "1 day streak" else "${b.currentStreak} day streak",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                Messages.buildStreak(b.currentStreak, b.longestStreak),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
    if (editing) {
        AmountDialog(
            title = "Today's amount",
            unit = habit.goalUnit,
            initial = b.todayAmount,
            onDismiss = { editing = false },
            onConfirm = { editing = false; onSetAmount(it) },
        )
    }
}

@Composable
private fun HeroCircleButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = if (enabled) 0.18f else 0.08f),
        modifier = Modifier
            .size(48.dp)
            .pressScale(src, 0.92f)
            .clickable(src, null, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun WeeklyBarsCard(b: BuildStats) {
    DaymarkCard(Modifier.fillMaxWidth()) {
        Text("Last 8 weeks", style = MaterialTheme.typography.titleMedium)
        Text(
            "Share of scheduled days done each week",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (b.weekly.isEmpty()) {
            Text("Your weeks will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val primary = MaterialTheme.colorScheme.primary
            val clean = DaymarkTheme.colors.clean.copy(alpha = 0.55f)
            BarChart(
                values = b.weekly.map { it.second.toFloat() },
                colors = b.weekly.indices.map { if (it == b.weekly.lastIndex) primary else clean },
                modifier = Modifier.fillMaxWidth().height(110.dp),
            )
            Spacer(Modifier.height(6.dp))
            ChartLabels(b.weekly.map { it.first.dayOfMonth.toString() })
            Spacer(Modifier.height(6.dp))
            Text(
                "Weeks starting ${b.weekly.first().first.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "2 of 3 habits done today", with a quick-log row for each of the other build habits. */
@Composable
private fun TodayOverviewCard(builders: List<HabitSummary>, selectedId: Long, vm: MainViewModel) {
    val haptics = rememberHaptics()
    val due = builders.filter { val b = it.build!!; b.todayScheduled || b.todayDone }
    val done = due.count { it.build!!.todayDone }
    val others = builders.filter { it.habit.id != selectedId }
    DaymarkCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                if (due.isEmpty()) "Rest day for all" else "$done of ${due.size} ${if (due.size == 1) "habit" else "habits"} done",
                style = MaterialTheme.typography.titleSmall,
                color = if (due.isNotEmpty() && done == due.size) DaymarkTheme.colors.clean else MaterialTheme.colorScheme.onSurface,
            )
        }
        others.forEach { s ->
            val b = s.build!!
            val h = s.habit
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.emoji, fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        when {
                            b.todayDone -> "Done"
                            !b.todayScheduled && b.todayAmount <= 0 -> "Rest day"
                            h.hasGoal -> "${amountWithUnit(b.todayAmount, h.goalUnit)} of ${amountWithUnit(b.todayTarget, h.goalUnit)}"
                            else -> "Not yet"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    b.todayDone -> Icon(Icons.Rounded.Check, "Done", tint = DaymarkTheme.colors.clean)
                    !b.todayScheduled && b.todayAmount <= 0 -> Unit
                    h.hasGoal -> TextButton(onClick = { haptics.tick(); vm.logProgress(h, if (h.goalStep > 0) h.goalStep else 1.0) }) {
                        Text("+${amountWithUnit(if (h.goalStep > 0) h.goalStep else 1.0, h.goalUnit)}")
                    }
                    else -> TextButton(onClick = { haptics.confirm(); vm.toggleDone(h) }) { Text("Done") }
                }
            }
        }
    }
}
