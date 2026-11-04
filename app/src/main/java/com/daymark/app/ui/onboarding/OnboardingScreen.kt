package com.daymark.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.daymark.app.ui.components.DaymarkCard
import com.daymark.app.ui.components.rememberHaptics
import com.daymark.app.ui.theme.DaymarkTheme
import com.daymark.app.ui.theme.LocalReducedMotion
import com.daymark.app.ui.theme.Motion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val suggestions = listOf("Alcohol", "Smoking", "Vaping", "Social media", "Gambling", "Junk food", "Doomscrolling")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onBegin: (name: String, start: Long) -> Unit) {
    val haptics = rememberHaptics()
    val zone = ZoneId.systemDefault()
    var name by rememberSaveable { mutableStateOf("") }
    var startedEarlier by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    // Gentle entrance: content rises into place once.
    val reduced = LocalReducedMotion.current
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, Motion.standard(0.7f)) }

    val c = DaymarkTheme.colors
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 48f },
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier.size(72.dp).background(Brush.linearGradient(listOf(c.heroGradientTop, c.accentWarm)), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(26.dp).background(Color.White.copy(alpha = 0.95f), CircleShape))
            }
            Spacer(Modifier.height(28.dp))
            Text("Welcome to Daymark", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Every day you mark is a day you made. Track your progress, understand your patterns, and keep every achievement — even after a slip.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Text("What are you moving away from?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Optional — e.g. Alcohol") },
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                suggestions.forEach { s -> SuggestionChip(onClick = { haptics.tick(); name = s }, label = { Text(s) }, shape = RoundedCornerShape(12.dp)) }
            }
            Spacer(Modifier.height(28.dp))
            Text("When did you start?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(!startedEarlier, { startedEarlier = false; start = System.currentTimeMillis() }, SegmentedButtonDefaults.itemShape(0, 2)) {
                    Text("Starting now")
                }
                SegmentedButton(startedEarlier, { startedEarlier = true }, SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text("Earlier")
                }
            }
            AnimatedVisibility(startedEarlier, enter = fadeIn() + expandVertically(Motion.standard()), exit = fadeOut() + shrinkVertically(Motion.standard())) {
                val dt = Instant.ofEpochMilli(start).atZone(zone)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DaymarkCard(Modifier.weight(1.3f), onClick = { showDate = true }, contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(dt.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    DaymarkCard(Modifier.weight(1f), onClick = { showTime = true }, contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(dt.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
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
                    onBegin(name, if (startedEarlier) minOf(start, System.currentTimeMillis()) else System.currentTimeMillis())
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Begin my journey", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDate) {
        val today = LocalDate.now()
        val dt = Instant.ofEpochMilli(start).atZone(zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        start = LocalDateTime.of(d, dt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDate = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (showTime) {
        val dt = Instant.ofEpochMilli(start).atZone(zone)
        val state = rememberTimePickerState(dt.hour, dt.minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    start = LocalDateTime.of(dt.toLocalDate(), LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli()
                    showTime = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
        )
    }
}
