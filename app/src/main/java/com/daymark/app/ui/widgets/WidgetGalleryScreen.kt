package com.daymark.app.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daymark.app.ui.MainViewModel
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.pressScale
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.widget.EventWidgetPinner
import com.daymark.app.widget.StyleSwatch
import com.daymark.app.widget.HomeWidgetPinner
import com.daymark.app.widget.WidgetKind
import com.daymark.app.widget.WidgetPreview
import com.daymark.app.widget.WidgetSnapshot
import com.daymark.app.widget.WidgetStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetGalleryScreen(vm: MainViewModel, onBack: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val habit by vm.selectedHabit.collectAsStateWithLifecycle()
    val relapses by vm.allRelapses.collectAsStateWithLifecycle()
    val urges by vm.allUrges.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val discreet = settings?.discreet ?: true
    val snap = remember(habit, relapses, urges, discreet) {
        habit?.let { WidgetSnapshot.from(it, relapses, urges = urges, discreet = discreet) }
    }
    var styleId by rememberSaveable { mutableStateOf(WidgetStyle.DEFAULT.id) }
    val style = WidgetStyle.of(styleId)

    fun pin(kind: WidgetKind) {
        haptics.confirm()
        if (kind == WidgetKind.EVENT) {
            // No event is chosen here, so the widget shows the one nearest to finish.
            EventWidgetPinner.pinEventWidget(context, null) { EventWidgetPinner.message(it)?.let(onMessage) }
            return
        }
        HomeWidgetPinner.pin(context, kind, style, habit?.id ?: 0L)
            .let { EventWidgetPinner.message(it) }?.let(onMessage)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Widgets") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Keep your progress in sight. Pick a style, then add any widget to your home screen — " +
                        "it shows ${habit?.let { "${it.emoji} ${it.displayName}" } ?: "your tracker"}. Long-press a placed widget to restyle it or switch tracker.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    items(WidgetStyle.entries) { s ->
                        val src = remember { MutableInteractionSource() }
                        Column(
                            Modifier.pressScale(src, 0.92f).clickable(src, null) { haptics.tick(); styleId = s.id },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            StyleSwatch(s, s == style, MaterialTheme.colorScheme.primary)
                            Text(
                                s.label, style = MaterialTheme.typography.labelMedium,
                                color = if (s == style) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(WidgetKind.entries) { kind ->
                DaymarkCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(kind.title, style = MaterialTheme.typography.titleMedium)
                            Text(kind.blurb, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        WidgetPreview(
                            kind, style, snap?.takeIf { it.onboarded },
                            Modifier.fillMaxWidth(if (kind == WidgetKind.RING) 0.5f else 1f),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    FilledTonalButton(
                        onClick = { pin(kind) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add to home screen")
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}
