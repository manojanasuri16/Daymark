package com.daymark.app.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daymark.app.domain.DayStatus
import com.daymark.app.domain.Format
import com.daymark.app.domain.Messages
import com.daymark.app.domain.Milestones

/** In-app (Compose) rendering of a widget, used by the style picker and widget gallery. Mirrors the Glance layouts. */
@Composable
fun WidgetPreview(kind: WidgetKind, style: WidgetStyle, snap: WidgetSnapshot?, modifier: Modifier = Modifier) {
    val text = colorResource(style.text)
    val soft = colorResource(style.soft)
    val accent = colorResource(style.accent)
    val track = colorResource(style.track)
    val shape = RoundedCornerShape(24.dp)
    val bg = Brush.linearGradient(listOf(colorResource(style.start), colorResource(style.center), colorResource(style.end)))
    val days = snap?.stats?.currentDays ?: 12
    val millis = snap?.stats?.currentMillis ?: (12 * 86_400_000L + 15_000_000L)
    val clock = (millis % 86_400_000L).let { "%d:%02d:%02d".format(it / 3_600_000, (it / 60_000) % 60, (it / 1000) % 60) }
    val habit = snap?.habit.orEmpty()
    val reasons = snap?.visibleReasons.orEmpty()
    val message = reasons.firstOrNull() ?: snap?.message ?: Messages.motivational.first()
    val next = Milestones.next(days)
    val progress = Milestones.progress(millis)

    val aspect = when (kind) { WidgetKind.RING -> 1f; WidgetKind.QUOTE -> 2.4f; WidgetKind.ACTIONS -> 3f; else -> 2f }
    Box(
        modifier
            .aspectRatio(aspect)
            .clip(shape)
            .background(bg)
            .then(if (style == WidgetStyle.MINIMAL) Modifier.border(1.dp, track, shape) else Modifier),
    ) {
        when (kind) {
            WidgetKind.STREAK -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    if (habit.isBlank()) "DAYMARK" else if (snap?.isReduce == true) "CUTTING DOWN: ${habit.uppercase()}" else "FREE FROM ${habit.uppercase()}",
                    color = soft, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$days", color = text, fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(if (days == 1L) "day" else "days", color = soft, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(clock, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(if (snap?.isReduce == true) todayLine(snap) else message, color = text, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().height(5.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = r)
                    drawRoundRect(accent, size = Size(maxOf(size.height, size.width * progress), size.height), cornerRadius = r)
                }
            }
            WidgetKind.RING -> Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = size.minDimension * 0.085f
                    val inset = stroke / 2 + size.minDimension * 0.02f
                    val d = size.minDimension - inset * 2
                    val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                    drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(accent, -90f, 360f * progress.coerceAtLeast(0.004f), false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$days", color = text, fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
                    Text(if (days == 1L) "day" else "days", color = soft, fontSize = 12.sp)
                    Text(clock, color = accent, fontSize = 11.sp)
                }
            }
            WidgetKind.HEATMAP -> Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Format.days(days), color = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("current streak", color = soft, fontSize = 10.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${if (snap == null) 94 else heatPercent(snap)}%", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("clean · 16 weeks", color = soft, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val clean = colorResource(style.clean)
                val relapse = colorResource(style.relapse)
                val untracked = colorResource(style.untracked)
                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                    val weeks = 16
                    val gap = 0.22f
                    val cell = minOf(size.width / (weeks + (weeks - 1) * gap), size.height / (7 + 6 * gap))
                    val ox = (size.width - (weeks * cell + (weeks - 1) * gap * cell)) / 2
                    val today = snap?.today ?: java.time.LocalDate.now()
                    val lastMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                    for (c in 0 until weeks) for (r in 0 until 7) {
                        val date = lastMonday.minusWeeks((weeks - 1 - c).toLong()).plusDays(r.toLong())
                        if (date.isAfter(today)) continue
                        val st = snap?.status(date) ?: sampleStatus(c, r)
                        val col = when (st) { DayStatus.CLEAN -> clean; DayStatus.RELAPSE -> relapse; DayStatus.UNTRACKED -> untracked }
                        drawRoundRect(col, Offset(ox + c * cell * (1 + gap), r * cell * (1 + gap)), Size(cell, cell), CornerRadius(cell * 0.28f))
                    }
                }
            }
            WidgetKind.QUOTE -> Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(if (reasons.isEmpty()) "✦  TODAY'S REMINDER" else "✦  YOUR REASON", color = soft, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Text(message, color = text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 3, lineHeight = 21.sp)
                Row {
                    Text("Day $days", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (habit.isNotBlank()) Text("  ·  free from ${habit.lowercase()}", color = soft, fontSize = 11.sp, maxLines = 1)
                }
            }
            WidgetKind.ACTIONS -> Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Triple("🛡️", "Urge?", true), Triple("📝", "Journal", false), Triple("＋", "Log", false)).forEach { (icon, label, em) ->
                    Column(
                        Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(16.dp)).background(if (em) track else colorResource(style.untracked)),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(icon, fontSize = 18.sp)
                        Text(label, color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            WidgetKind.STATS -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(if (habit.isBlank()) "DAYMARK" else habit.uppercase(), color = soft, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.weight(1f))
                val items = buildList {
                    if (snap == null || snap.tracksMoney) add((if (snap == null) Format.money(4200.0) else Format.money(snap.moneyKept)) to "kept")
                    add("${snap?.urgeCount ?: 9}" to "urges ridden out")
                    add("${snap?.stats?.totalCleanDays ?: 40}" to "clean days")
                    add("${snap?.stats?.longestDays ?: 21}d" to "best streak")
                }.take(4)
                items.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (v, l) ->
                            Column(Modifier.weight(1f)) {
                                Text(v, color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(l, color = soft, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun sampleStatus(c: Int, r: Int): DayStatus = when {
    c < 2 -> DayStatus.UNTRACKED
    (c * 7 + r) % 23 == 5 -> DayStatus.RELAPSE
    else -> DayStatus.CLEAN
}

private fun heatPercent(snap: WidgetSnapshot): Int {
    val start = snap.today.minusDays((snap.today.dayOfWeek.value - 1).toLong()).minusWeeks(15)
    var clean = 0; var tracked = 0; var d = start
    while (!d.isAfter(snap.today)) {
        when (snap.status(d)) { DayStatus.CLEAN -> { clean++; tracked++ }; DayStatus.RELAPSE -> tracked++; else -> {} }
        d = d.plusDays(1)
    }
    return if (tracked == 0) 0 else clean * 100 / tracked
}

/** A round swatch for the style picker. */
@Composable
fun StyleSwatch(style: WidgetStyle, selected: Boolean, ring: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(52.dp)
            .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier)
            .padding(if (selected) 6.dp else 3.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colorResource(style.start), colorResource(style.center), colorResource(style.end))))
            .border(1.dp, colorResource(style.track), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(colorResource(style.accent)))
    }
}
