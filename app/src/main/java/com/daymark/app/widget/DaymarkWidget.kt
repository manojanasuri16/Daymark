package com.daymark.app.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.daymark.app.container
import com.daymark.app.domain.Format
import com.daymark.app.domain.Messages
import com.daymark.app.domain.Milestones
import com.daymark.app.domain.StreakEngine
import com.daymark.app.domain.StreakStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        try {
            DaymarkWidget().updateAll(context)
        } catch (_: Exception) {
            // Widget refresh is best-effort.
        }
    }
}

private data class WidgetData(
    val onboarded: Boolean,
    val habit: String,
    val stats: StreakStats,
    val message: String,
)

private val widgetBg = ColorProvider(day = Color(0xFF2B7A67), night = Color(0xFF14302A))
private val white = ColorProvider(day = Color.White, night = Color.White)
private val whiteSoft = ColorProvider(day = Color(0xCCFFFFFF), night = Color(0xCCFFFFFF))

class DaymarkWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 60.dp), DpSize(180.dp, 110.dp), DpSize(260.dp, 160.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext
        // Initial snapshot so the first frame is correct, then live flows keep a running session fresh.
        val initialSettings = withContext(Dispatchers.IO) { app.container.settings.current() }
        val initialRelapses = withContext(Dispatchers.IO) { app.container.relapses.all() }
        provideContent {
            val settings by app.container.settings.settings.collectAsState(initialSettings)
            val relapses by app.container.relapses.relapses.collectAsState(initialRelapses)
            val now = System.currentTimeMillis()
            val data = WidgetData(
                onboarded = settings.onboarded,
                habit = settings.habitName,
                stats = StreakEngine.compute(settings.journeyStart, relapses.map { it.timestamp }, now),
                message = Messages.forDate(LocalDate.now()),
            )
            WidgetContent(context, data)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData) {
    val size = LocalSize.current
    val open = actionStartActivity(
        Intent().setComponent(ComponentName(context.packageName, "com.daymark.app.ui.MainActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
    val isLarge = size.width >= 260.dp && size.height >= 160.dp
    val isMedium = !isLarge && size.width >= 180.dp && size.height >= 110.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(widgetBg)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(open),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (!data.onboarded) {
            Text(
                text = "Open Daymark to begin your journey",
                style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 3,
            )
        } else {
            val days = data.stats.currentDays
            val rem = data.stats.currentMillis % StreakStats.DAY_MS
            val hours = rem / 3_600_000L
            val minutes = (rem / 60_000L) % 60
            val sub = "${hours}h ${minutes}m"
            if (!isMedium && !isLarge) {
                Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$days",
                        style = TextStyle(color = white, fontSize = 32.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = if (days == 1L) "day" else "days",
                        style = TextStyle(color = whiteSoft, fontSize = 12.sp),
                    )
                }
            } else {
                Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (data.habit.isBlank()) "DAYMARK" else "DAYMARK  ·  ${data.habit.uppercase()}",
                        style = TextStyle(color = whiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$days",
                            style = TextStyle(color = white, fontSize = 40.sp, fontWeight = FontWeight.Bold),
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = if (days == 1L) "day" else "days",
                            style = TextStyle(color = whiteSoft, fontSize = 14.sp),
                            modifier = GlanceModifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        text = "+ $sub",
                        style = TextStyle(color = whiteSoft, fontSize = 12.sp),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        text = data.message,
                        style = TextStyle(color = white, fontSize = 12.sp),
                        maxLines = 2,
                    )
                    if (isLarge) {
                        Spacer(GlanceModifier.height(8.dp))
                        val next = Milestones.next(days)
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            Text(
                                text = "Best ${Format.days(data.stats.longestDays)}",
                                style = TextStyle(color = whiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.width(12.dp))
                            if (next != null) {
                                Text(
                                    text = "Next: ${next.title} in ${Format.days(next.days - days)}",
                                    style = TextStyle(color = whiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class DaymarkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DaymarkWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                val pending: BroadcastReceiver.PendingResult = goAsync()
                val app = context.applicationContext
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        WidgetUpdater.updateAll(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
