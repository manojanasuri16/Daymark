package com.daymark.app.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.daymark.app.domain.Options
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.habits.HabitFields
import com.daymark.app.ui.theme.DaymarkTheme
import com.daymark.app.ui.theme.LocalReducedMotion
import com.daymark.app.ui.theme.Motion

@Composable
fun OnboardingScreen(onBegin: (name: String, emoji: String, start: Long) -> Unit) {
    val haptics = rememberHaptics()
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf(Options.emojis.first()) }
    var startedEarlier by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    // Gentle entrance: content rises into place once.
    val reduced = LocalReducedMotion.current
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, Motion.standard(0.7f)) }

    val c = DaymarkTheme.colors
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 48f },
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.size(72.dp).background(Brush.linearGradient(listOf(c.heroGradientTop, c.accentWarm)), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(26.dp).background(Color.White.copy(alpha = 0.95f), CircleShape)) }
            Spacer(Modifier.height(28.dp))
            Text("Welcome to Daymark", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text(
                "Every day you mark is a day you made. Track your progress, understand your patterns, and keep every achievement — even after a slip.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                HabitFields(
                    name = name, onName = { name = it },
                    emoji = emoji, onEmoji = { emoji = it },
                    startedEarlier = startedEarlier, onStartedEarlier = { startedEarlier = it; if (!it) start = System.currentTimeMillis() },
                    start = start, onStart = { start = it },
                    isNew = true,
                )
                Text(
                    "You can add more trackers later — each keeps its own streaks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Shield, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Private by design: no account, no cloud, no tracking. Your data never leaves this phone unless you export it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f, fill = true))
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    haptics.confirm()
                    onBegin(name, emoji, if (startedEarlier) minOf(start, System.currentTimeMillis()) else System.currentTimeMillis())
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Begin my journey", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
