package com.daymark.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.domain.Habit
import com.daymark.app.domain.Messages
import com.daymark.app.ui.HabitSummary
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.ProgressRing
import com.daymark.app.ui.components.amountWithUnit
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.theme.DaymarkTheme
import com.daymark.app.ui.widgets.AddToHomeSheet
import com.daymark.app.widget.EventWidgetPinner
import com.daymark.app.widget.HomeWidgetPinner
import com.daymark.app.widget.WidgetOffer
import com.daymark.app.widget.WidgetSnapshot
import java.time.LocalTime

/**
 * Today tab when there's more than one tracker: every tracker at a glance as a tile.
 * Tapping a tile opens that tracker's full page; long-press offers "Add to home screen".
 */
@Composable
fun TrackersHome(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onOpen: (Habit) -> Unit,
    onAddHabit: () -> Unit,
) {
    val overview by vm.overview.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val allRelapses by vm.allRelapses.collectAsStateWithLifecycle()
    val allUrges by vm.allUrges.collectAsStateWithLifecycle()
    val allCompletions by vm.allCompletions.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptics = rememberHaptics()

    var sheetHabitId by rememberSaveable { mutableStateOf<Long?>(null) }
    val sheetHabit = overview.firstOrNull { it.habit.id == sheetHabitId }?.habit
    if (sheetHabit != null) {
        val discreet = settings?.discreet ?: true
        val snap = remember(sheetHabit, allRelapses, allUrges, allCompletions, discreet) {
            WidgetSnapshot.from(sheetHabit, allRelapses, urges = allUrges, discreet = discreet, completions = allCompletions)
        }
        AddToHomeSheet(
            habit = sheetHabit, snapshot = snap,
            onDismiss = { sheetHabitId = null },
            onPin = { kind, style ->
                sheetHabitId = null
                EventWidgetPinner.message(HomeWidgetPinner.pin(context, kind, style, sheetHabit.id))?.let(vm::say)
            },
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("header", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)) {
                Text(
                    Messages.greeting(LocalTime.now().hour),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Your trackers", style = MaterialTheme.typography.headlineLarge)
            }
        }
        items(overview, key = { it.habit.id }) { summary ->
            TrackerTile(
                summary,
                onClick = { haptics.tick(); onOpen(summary.habit) },
                onLongClick = {
                    haptics.tick()
                    if (settings?.homeHintShows != WidgetOffer.HINT_DONE) vm.setHomeHintShows(WidgetOffer.HINT_DONE)
                    sheetHabitId = summary.habit.id
                },
            )
        }
        item("add") { AddTrackerTile(onAddHabit) }
        item("caption", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Tap a tracker to open it. Hold one to add it to your home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackerTile(summary: HabitSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    val h = summary.habit
    val build = summary.build
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .pressScale(src, 0.96f)
            .clip(shape)
            .combinedClickable(
                interactionSource = src, indication = null,
                onClickLabel = "Open ${h.displayName}", onClick = onClick,
                onLongClickLabel = "Add to home screen", onLongClick = onLongClick,
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.emoji, fontSize = 26.sp, modifier = Modifier.weight(1f))
                if (build != null) {
                    Box(contentAlignment = Alignment.Center) {
                        ProgressRing(
                            progress = if (build.todayDone) 1f else build.todayProgress,
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 4.dp,
                        )
                        if (build.todayDone) {
                            Icon(Icons.Rounded.Check, "Done today", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                h.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    h.isBuild -> "Building"
                    h.isReduce -> "Cutting down"
                    else -> "Breaking free"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            val days = if (build != null) build.currentStreak.toLong() else summary.stats.currentDays
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    days.toString(),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (build == null) DaymarkTheme.colors.clean else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    when {
                        build != null -> "day streak"
                        days == 1L -> "day"
                        else -> "days"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                when {
                    build == null -> "Best ${summary.stats.longestDays} d"
                    build.todayDone -> "Done today"
                    !build.todayScheduled -> "Rest day"
                    h.hasGoal -> "${amountWithUnit(build.todayAmount, h.goalUnit)} of ${amountWithUnit(build.todayTarget, h.goalUnit)} today"
                    else -> "Not yet today"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AddTrackerTile(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    Surface(
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(196.dp).pressScale(src, 0.96f).clip(shape).clickable(src, null, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Track something else", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
