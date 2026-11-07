package com.daymark.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.IconButton
import com.daymark.app.domain.Habit
import com.daymark.app.ui.components.AnimatedText
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.Format
import com.daymark.app.domain.Messages
import com.daymark.app.domain.Milestone
import com.daymark.app.domain.Milestones
import com.daymark.app.domain.StreakStats
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.ProgressRing
import com.daymark.app.ui.components.RollingNumber
import com.daymark.app.ui.components.SectionHeader
import com.daymark.app.ui.components.StatTile
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.theme.DaymarkTheme
import com.daymark.app.ui.theme.HeroNumber
import com.daymark.app.ui.theme.Motion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DashboardScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onLogSlip: () -> Unit,
    onUrge: () -> Unit,
    onJournal: () -> Unit,
    onOpenCalendar: () -> Unit,
    onAddHabit: () -> Unit,
    onEditHabit: (Habit) -> Unit,
) {
    val selected by vm.selectedHabit.collectAsStateWithLifecycle()
    val overview by vm.overview.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val urges by vm.urges.collectAsStateWithLifecycle()
    val entries by vm.relapses.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val habit = selected?.name.orEmpty()
    val reduce = selected?.isReduce == true
    val haptics = rememberHaptics()
    val chipsState = rememberLazyListState()
    // Keep the selected tracker's chip in view when switching or when trackers are added/removed.
    LaunchedEffect(selected?.id, overview.size) {
        val i = overview.indexOfFirst { it.habit.id == selected?.id }
        if (i >= 0) chipsState.animateScrollToItem(if (i == 0) 0 else i - 1)
    }
    val today = remember(now / 3_600_000) { LocalDate.now() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("header") {
            Row(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Messages.greeting(LocalTime.now().hour),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedText(
                        when {
                            habit.isBlank() -> "Your journey"
                            reduce -> "Cutting down: $habit"
                            else -> "Free from $habit"
                        },
                        MaterialTheme.typography.headlineLarge,
                    )
                }
                selected?.let { h ->
                    IconButton(onClick = { onEditHabit(h) }) {
                        Icon(Icons.Rounded.Tune, "Edit tracker", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item("trackers") {
            LazyRow(state = chipsState, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                items(overview, key = { it.habit.id }) { summary ->
                    TrackerChip(
                        emoji = summary.habit.emoji,
                        label = summary.habit.displayName,
                        days = summary.stats.currentDays,
                        selected = summary.habit.id == selected?.id,
                        onClick = { haptics.tick(); vm.selectHabit(summary.habit.id) },
                    )
                }
                item("add") { AddTrackerChip(compact = overview.size > 1, onClick = onAddHabit) }
            }
        }
        item("hero") { HeroCard(stats) }
        if (reduce) {
            item("today") {
                val zone = ZoneId.systemDefault()
                val used = entries.count { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == today }
                val limit = selected?.dailyLimit ?: 0
                DaymarkCard(contentPadding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$used of $limit",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (used > limit) DaymarkTheme.colors.relapse else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            when {
                                used > limit -> "Over today's limit. Tomorrow is a fresh page."
                                used == limit -> "At your limit for today."
                                else -> "Within your limit. Nice."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                }
            }
        }
        item("celebrate") {
            MilestoneBanner(stats) { m ->
                val s = settings ?: return@MilestoneBanner
                val label = if (s.discreet) null else selected?.displayName
                com.daymark.app.ui.share.ShareCard.share(
                    context,
                    com.daymark.app.ui.share.ShareCard.render(stats.currentDays, m.title, m.subtitle, label, com.daymark.app.ui.theme.Accent.of(s.accent)),
                )
            }
        }
        item("actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(
                    Icons.Rounded.Shield, "Feeling an urge?", "Ride it out",
                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer,
                    Modifier.weight(1f), onUrge,
                )
                ActionCard(
                    Icons.Rounded.EditNote, "Journal", "How's today?",
                    MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f), onJournal,
                )
            }
        }
        item("stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = Format.days(stats.longestDays),
                    label = if (stats.longestIsCurrent && stats.relapseCount > 0) "Best streak · now!" else "Best streak",
                    icon = Icons.Rounded.EmojiEvents,
                    tint = DaymarkTheme.colors.accentWarm,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = Format.days(stats.totalCleanDays.toLong()),
                    label = if (reduce) "Days within limit" else "Total clean days",
                    icon = Icons.Rounded.Eco,
                    tint = DaymarkTheme.colors.clean,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item("stats2") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val h = selected
                if (h != null && h.tracksMoney) {
                    StatTile(
                        value = Format.money(h.moneyKept(stats.totalCleanDays)),
                        label = "Money kept",
                        icon = Icons.Rounded.Savings,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                StatTile(
                    value = urges.size.toString(),
                    label = if (urges.size == 1) "Urge ridden out" else "Urges ridden out",
                    icon = Icons.Rounded.Shield,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item("reasons") { ReasonsCard(selected?.reasons.orEmpty(), today) { selected?.let(onEditHabit) } }
        item("quote") {
            DaymarkCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.FormatQuote, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Messages.forDate(today),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        item("ach_header") {
            val earned = Milestones.earned(stats.longestDays).size
            SectionHeader("Achievements") {
                Text(
                    "$earned of ${Milestones.all.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("ach") { AchievementsRow(stats) }
        item("journey") { JourneyCard(stats, onOpenCalendar) }
        item("slip") { SlipCard(reduce, onLogSlip) }
    }
}

@Composable
private fun HeroCard(stats: StreakStats) {
    val colors = DaymarkTheme.colors
    val remainder = stats.currentMillis % StreakStats.DAY_MS
    val h = remainder / 3_600_000
    val m = (remainder / 60_000) % 60
    val s = (remainder / 1000) % 60
    val next = Milestones.next(stats.currentDays)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(colors.heroGradientTop, colors.heroGradientBottom)))
            .padding(vertical = 28.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(248.dp), contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = Milestones.progress(stats.currentMillis),
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    color = Color.White.copy(alpha = 0.92f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RollingNumber(stats.currentDays, HeroNumber, Color.White)
                    Text(
                        if (stats.currentDays == 1L) "day" else "days",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "%02d:%02d:%02d".format(h, m, s),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFeatureSettings = "tnum", letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
                        ),
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (next == null) "Every milestone reached. Extraordinary."
                else {
                    val left = next.days - stats.currentDays
                    "${next.title} in ${Format.days(left)}"
                },
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            val started = remember(stats.currentStart) {
                Instant.ofEpochMilli(stats.currentStart).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
            }
            Text(
                "Since $started",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun MilestoneBanner(stats: StreakStats, onShare: (Milestone) -> Unit) {
    val hit = Milestones.all.lastOrNull { it.days.toLong() == stats.currentDays }
    AnimatedVisibility(
        visible = hit != null,
        enter = fadeIn(Motion.standard()) + expandVertically(Motion.standard()),
        exit = fadeOut(Motion.standard()) + shrinkVertically(Motion.standard()),
    ) {
        if (hit != null) {
            DaymarkCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("You reached ${hit.title}", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(hit.subtitle, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                    }
                    IconButton(onClick = { onShare(hit) }) {
                        Icon(Icons.Rounded.Share, "Share milestone", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementsRow(stats: StreakStats) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val next = Milestones.next(stats.currentDays)
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(Milestones.all, key = { it.days }) { m ->
                Badge(
                    milestone = m,
                    earned = m.days <= stats.longestDays,
                    inProgress = m == next,
                    progress = if (m == next) Milestones.progress(stats.currentMillis) else 0f,
                    selected = selected == m.days,
                    onClick = { selected = if (selected == m.days) null else m.days },
                )
            }
        }
        val sel = Milestones.all.firstOrNull { it.days == selected }
        AnimatedVisibility(sel != null, enter = fadeIn() + expandVertically(Motion.standard()), exit = fadeOut() + shrinkVertically(Motion.standard())) {
            if (sel != null) {
                val earned = sel.days <= stats.longestDays
                Text(
                    if (earned) "${sel.title} — ${sel.subtitle}"
                    else "${sel.title} unlocks at ${Format.days(sel.days.toLong())}. ${Format.days(sel.days - stats.currentDays)} to go.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Badge(
    milestone: Milestone,
    earned: Boolean,
    inProgress: Boolean,
    progress: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val colors = DaymarkTheme.colors
    val bg = if (earned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val selScale by animateFloatAsState(if (selected) 1.05f else 1f, Motion.bouncy(), label = "badgeSel")
    Column(
        Modifier
            .width(84.dp)
            .graphicsLayer { scaleX = selScale; scaleY = selScale }
            .pressScale(source, 0.94f)
            .clickable(source, null) { haptics.tick(); onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            if (inProgress) {
                ProgressRing(progress, Modifier.fillMaxSize(), strokeWidth = 4.dp, color = colors.clean)
            }
            Surface(
                shape = CircleShape,
                color = bg,
                modifier = Modifier
                    .size(if (inProgress) 56.dp else 64.dp)
                    .then(if (!earned && !inProgress) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            earned -> milestoneIcon(milestone.days)
                            else -> Icons.Rounded.Lock
                        },
                        contentDescription = null,
                        tint = if (earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(if (earned) 28.dp else 20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            shortLabel(milestone.days),
            style = MaterialTheme.typography.labelLarge,
            color = if (earned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TrackerChip(emoji: String, label: String, days: Long, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        Motion.standard(), label = "chipBg",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        modifier = Modifier.pressScale(src, 0.95f).clickable(src, null, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(
                "${days}d", style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddTrackerChip(compact: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.pressScale(src, 0.95f).clickable(src, null, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Text("Track something else", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun milestoneIcon(days: Int) = when {
    days < 7 -> Icons.Rounded.WbSunny
    days < 90 -> Icons.Rounded.Spa
    else -> Icons.Rounded.EmojiEvents
}

private fun shortLabel(days: Int) = when {
    days >= 365 && days % 365 == 0 -> "${days / 365}y"
    days >= 30 && days % 30 == 0 -> "${days / 30}mo"
    days == 270 -> "9mo"
    days == 180 -> "6mo"
    days % 7 == 0 && days < 30 -> "${days / 7}w"
    else -> "${days}d"
}

@Composable
private fun JourneyCard(stats: StreakStats, onOpenCalendar: () -> Unit) {
    val started = remember(stats.journeyStart) {
        Instant.ofEpochMilli(stats.journeyStart).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    }
    DaymarkCard(onClick = onOpenCalendar) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Your journey", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Began $started · ${stats.streaks.size} ${if (stats.streaks.size == 1) "streak" else "streaks"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SlipCard(reduce: Boolean, onLogSlip: () -> Unit) {
    DaymarkCard(onClick = onLogSlip, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (reduce) "Log a use" else "Had a slip?", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (reduce) "Counting honestly is how cutting down works. Only days over your limit end a streak."
                    else "Log it privately. Your history and achievements stay with you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    DaymarkCard(modifier = modifier, onClick = { haptics.tick(); onClick() }, color = color, contentPadding = 16.dp) {
        Icon(icon, null, tint = content)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.75f))
    }
}

/** The user's own reasons — one per day, tap to see the next. */
@Composable
private fun ReasonsCard(reasons: List<String>, today: LocalDate, onAdd: () -> Unit) {
    if (reasons.isEmpty()) {
        DaymarkCard(onClick = onAdd, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FormatQuote, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Why are you doing this?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Write down your reasons. They'll be here when an urge hits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }
    var offset by rememberSaveable { mutableStateOf(0) }
    val i = (((today.toEpochDay() + offset) % reasons.size).toInt() + reasons.size) % reasons.size
    DaymarkCard(onClick = { offset++ }, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
        Text("Your reason", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        AnimatedText(
            reasons[i],
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (reasons.size > 1) {
            Text("${i + 1} of ${reasons.size} · tap for the next", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f), modifier = Modifier.padding(top = 6.dp))
        }
    }
}
