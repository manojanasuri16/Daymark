package com.daymark.app.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as startIntent
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.daymark.app.R
import com.daymark.app.container
import com.daymark.app.domain.DayStatus
import com.daymark.app.domain.Format
import com.daymark.app.domain.Messages
import com.daymark.app.domain.Milestones
import com.daymark.app.notify.Routes
import com.daymark.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val StyleKey = stringPreferencesKey("style")
val HabitKey = longPreferencesKey("habit")
private val QuoteOffsetKey = intPreferencesKey("quote_offset")

/** Shared plumbing: live data flows, per-widget style, themed frame, tap-to-open. */
abstract class DaymarkGlanceWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    /**
     * Fixed breakpoints: the launcher picks the largest layout that fits, on its own. (Exact mode re-measured on every
     * refresh, so an update pushed while the app was open could leave a widget stuck in the wrong layout.)
     */
    override val sizeMode: SizeMode get() = SizeMode.Responsive(sizes)

    abstract val kind: WidgetKind
    abstract val sizes: Set<DpSize>

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext
        // A new widget claims the pending default style once, so later gallery changes don't restyle it.
        val s0 = withContext(Dispatchers.IO) { app.container.settings.current() }
        val h0 = withContext(Dispatchers.IO) { app.container.habits.all() }
        val r0 = withContext(Dispatchers.IO) { app.container.relapses.all() }
        val u0 = withContext(Dispatchers.IO) { app.container.urges.allEntities().map { it.toRecord() } }
        updateAppWidgetState(app, id) { prefs ->
            if (prefs[StyleKey] == null) prefs[StyleKey] = WidgetDefaults.get(app, kind).id
            if (prefs[HabitKey] == null && h0.isNotEmpty()) {
                prefs[HabitKey] = WidgetDefaults.habit(app, kind).takeIf { d -> h0.any { it.id == d } } ?: s0.selectedHabitId
            }
        }
        provideContent {
            val settings by app.container.settings.settings.collectAsState(s0)
            val habits by app.container.habits.habits.collectAsState(h0)
            val relapses by app.container.relapses.relapses.collectAsState(r0)
            val urges by app.container.urges.urges.collectAsState(u0)
            val prefs = currentState<Preferences>()
            val style = WidgetStyle.of(prefs[StyleKey])
            val habit = if (settings.onboarded) WidgetSnapshot.resolveHabit(habits, prefs[HabitKey], settings) else null
            val snap = WidgetSnapshot.from(habit, relapses, urges = urges, discreet = settings.discreet)
            Box(
                GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(ImageProvider(style.background))
                    .cornerRadius(android.R.dimen.system_app_widget_background_radius)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                if (!snap.onboarded) NotStarted(style) else Content(snap, style, prefs)
            }
        }
    }

    @Composable
    abstract fun Content(snap: WidgetSnapshot, style: WidgetStyle, prefs: Preferences)
}

// region Shared building blocks
@Composable
private fun T(
    text: String,
    size: TextUnit,
    color: Int,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
    modifier: GlanceModifier = GlanceModifier,
) = Text(
    text = text,
    style = TextStyle(color = dayNight(LocalContext.current, color), fontSize = size, fontWeight = weight, textAlign = align),
    maxLines = maxLines,
    modifier = modifier,
)

/** Resolves a colour resource for both light and dark so the launcher can switch without an update. */
private fun dayNight(context: Context, @androidx.annotation.ColorRes id: Int): androidx.glance.unit.ColorProvider {
    fun ctx(night: Boolean): Context {
        val cfg = android.content.res.Configuration(context.resources.configuration)
        cfg.uiMode = (cfg.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
        return context.createConfigurationContext(cfg)
    }
    return androidx.glance.color.ColorProvider(
        day = androidx.compose.ui.graphics.Color(ctx(false).getColor(id)),
        night = androidx.compose.ui.graphics.Color(ctx(true).getColor(id)),
    )
}

/** A system Chronometer — ticks every second on the home screen with zero app wake-ups. */
@Composable
private fun LiveClock(snap: WidgetSnapshot, color: Int, sizeSp: Float) {
    val context = LocalContext.current
    val rv = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        setChronometer(R.id.chrono, SystemClock.elapsedRealtime() - snap.intoDayMillis, null, true)
        setTextColor(R.id.chrono, context.getColor(color))
        setTextViewTextSize(R.id.chrono, TypedValue.COMPLEX_UNIT_SP, sizeSp)
    }
    AndroidRemoteViews(rv)
}

@Composable
private fun NotStarted(style: WidgetStyle) {
    Column(
        GlanceModifier.fillMaxSize().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T("Daymark", 15.sp, style.text, FontWeight.Bold)
        T("Tap to begin your journey", 12.sp, style.soft, align = TextAlign.Center, maxLines = 2)
    }
}

/** A frosted rounded tile, tinted with the style's translucent track colour. */
@Composable
private fun tileModifier(style: WidgetStyle, strong: Boolean = false): GlanceModifier =
    GlanceModifier
        .background(dayNight(LocalContext.current, if (strong) style.track else style.untracked))
        .cornerRadius(18.dp)

@Composable
private fun Icon(@androidx.annotation.DrawableRes id: Int, color: Int, size: Dp, description: String? = null) = Image(
    ImageProvider(id), contentDescription = description,
    colorFilter = ColorFilter.tint(dayNight(LocalContext.current, color)),
    modifier = GlanceModifier.size(size),
)

/** Small rounded label at the top of a widget: "🌊 DAYMARK". */
@Composable
private fun HeaderPill(snap: WidgetSnapshot, style: WidgetStyle) {
    Row(tileModifier(style).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        T(headerLabel(snap), 10.sp, style.text, FontWeight.Medium)
    }
}

private fun dpToPx(context: Context, dp: Dp): Int = (dp.value * context.resources.displayMetrics.density).toInt()

private fun daysWord(d: Long) = if (d == 1L) "day" else "days"

/** Cut-down mode: "Today 1 of 2". */
internal fun todayLine(snap: WidgetSnapshot) = "Today ${snap.todayUses} of ${snap.dailyLimit}" +
    if (snap.todayUses > snap.dailyLimit) " · fresh page tomorrow" else ""

private fun headerLabel(snap: WidgetSnapshot) =
    (if (snap.habit.isBlank()) "DAYMARK" else if (snap.isReduce) "CUTTING DOWN: ${snap.habit.uppercase()}" else "FREE FROM ${snap.habit.uppercase()}").let { if (snap.emoji.isBlank()) it else "${snap.emoji}  $it" }
// endregion

/** 1 — Streak: big day count, live h:m:s, message, milestone progress. Adapts from 2×1 to 4×3. */
class StreakWidget : DaymarkGlanceWidget() {
    override val kind = WidgetKind.STREAK
    override val sizes = setOf(DpSize(110.dp, 50.dp), DpSize(250.dp, 50.dp), DpSize(150.dp, 110.dp), DpSize(220.dp, 150.dp), DpSize(220.dp, 175.dp))

    @Composable
    override fun Content(snap: WidgetSnapshot, style: WidgetStyle, prefs: Preferences) {
        val size = LocalSize.current
        val context = LocalContext.current
        val days = snap.stats.currentDays
        if (size.height < 100.dp) {
            // Slim layout (2×1 … 4×1)
            Row(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                T("$days", (size.height.value * 0.5f).coerceIn(26f, 40f).sp, style.text, FontWeight.Bold)
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    T(daysWord(days), 12.sp, style.soft, FontWeight.Medium)
                    LiveClock(snap, style.accent, 12f)
                }
                if (size.width >= 240.dp) {
                    Spacer(GlanceModifier.width(14.dp))
                    T(if (snap.isReduce) todayLine(snap) else snap.message, 12.sp, style.text, maxLines = 2, modifier = GlanceModifier.defaultWeight())
                }
            }
            return
        }
        val roomy = size.height >= 170.dp && size.width >= 220.dp
        Column(GlanceModifier.fillMaxSize().padding(16.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeaderPill(snap, style)
                Spacer(GlanceModifier.defaultWeight())
                if (size.width >= 220.dp) {
                    Icon(R.drawable.ic_w_trophy, style.soft, 12.dp)
                    Spacer(GlanceModifier.width(4.dp))
                    T("${snap.stats.longestDays}d", 11.sp, style.soft, FontWeight.Medium)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                T("$days", (size.height.value * 0.3f).coerceIn(34f, 64f).sp, style.text, FontWeight.Bold)
                Spacer(GlanceModifier.width(6.dp))
                Column {
                    T(daysWord(days), 14.sp, style.soft, FontWeight.Medium)
                    Spacer(GlanceModifier.height(8.dp))
                }
                Spacer(GlanceModifier.defaultWeight())
                Box(tileModifier(style).padding(horizontal = 10.dp, vertical = 4.dp)) { LiveClock(snap, style.accent, 13f) }
            }
            if (size.height >= 150.dp) {
                Spacer(GlanceModifier.height(8.dp))
                if (snap.isReduce) T(todayLine(snap), 12.sp, style.text, FontWeight.Medium)
                else T(snap.message, 12.sp, style.text, maxLines = if (roomy) 2 else 1)
            }
            if (roomy) {
                Spacer(GlanceModifier.height(10.dp))
                val next = Milestones.next(days)
                val barW = size.width - 32.dp
                Image(
                    ImageProvider(WidgetArt.bar(dpToPx(context, barW), dpToPx(context, 5.dp), Milestones.progress(snap.stats.currentMillis), style.palette(context))),
                    contentDescription = null,
                    contentScale = androidx.glance.layout.ContentScale.FillBounds,
                    modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                )
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    T(
                        next?.let { "${it.title} in ${Format.days(it.days - days)}" } ?: "All milestones reached",
                        11.sp, style.soft, modifier = GlanceModifier.defaultWeight(),
                    )
                    T("${(Milestones.progress(snap.stats.currentMillis) * 100).toInt()}%", 11.sp, style.accent, FontWeight.Medium)
                }
            }
        }
    }
}

/** 2 — Progress ring toward the next milestone, with the streak in the centre. */
class RingWidget : DaymarkGlanceWidget() {
    override val kind = WidgetKind.RING
    override val sizes = setOf(DpSize(100.dp, 100.dp), DpSize(170.dp, 170.dp), DpSize(110.dp, 150.dp), DpSize(250.dp, 110.dp))

    @Composable
    override fun Content(snap: WidgetSnapshot, style: WidgetStyle, prefs: Preferences) {
        val size = LocalSize.current
        val context = LocalContext.current
        val days = snap.stats.currentDays
        val next = Milestones.next(days)
        val wide = size.width - size.height >= 110.dp
        val tall = !wide && size.height - size.width >= 34.dp
        val pad = 12.dp
        val ring: Dp = minOf(size.width - pad * 2, size.height - pad * 2 - if (tall) 28.dp else 0.dp).coerceAtLeast(48.dp)
        val ringContent = @Composable {
            Box(GlanceModifier.size(ring), contentAlignment = Alignment.Center) {
                Image(
                    ImageProvider(WidgetArt.ring(dpToPx(context, ring), Milestones.progress(snap.stats.currentMillis), style.palette(context))),
                    contentDescription = "Progress to next milestone",
                    modifier = GlanceModifier.size(ring),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T("$days", (ring.value * 0.3f).coerceIn(20f, 54f).sp, style.text, FontWeight.Bold, align = TextAlign.Center)
                    T(daysWord(days), (ring.value * 0.085f).coerceIn(10f, 14f).sp, style.soft, FontWeight.Medium, align = TextAlign.Center)
                    if (ring >= 120.dp) LiveClock(snap, style.accent, (ring.value * 0.075f).coerceIn(10f, 13f))
                }
            }
        }
        when {
            wide -> Row(GlanceModifier.fillMaxSize().padding(pad), verticalAlignment = Alignment.CenterVertically) {
                ringContent()
                Spacer(GlanceModifier.width(14.dp))
                Column(GlanceModifier.defaultWeight()) {
                    T("NEXT MILESTONE", 10.sp, style.soft, FontWeight.Medium)
                    T(next?.title ?: "Every milestone", 16.sp, style.text, FontWeight.Bold, maxLines = 2)
                    T(next?.let { "${Format.days(it.days - days)} to go" } ?: "reached. Extraordinary.", 12.sp, style.accent, FontWeight.Medium)
                    Spacer(GlanceModifier.height(6.dp))
                    T("Best streak ${Format.days(snap.stats.longestDays)}", 11.sp, style.soft)
                }
            }
            else -> Column(
                GlanceModifier.fillMaxSize().padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ringContent()
                if (tall) {
                    Spacer(GlanceModifier.height(6.dp))
                    T(next?.let { "${it.title} · ${it.days - days}d" } ?: "All milestones", 11.sp, style.soft, FontWeight.Medium, align = TextAlign.Center)
                }
            }
        }
    }
}

/** 3 — Heatmap of recent weeks: clean / slip / untracked, today outlined. */
class HeatmapWidget : DaymarkGlanceWidget() {
    override val kind = WidgetKind.HEATMAP
    override val sizes = setOf(DpSize(180.dp, 100.dp), DpSize(250.dp, 130.dp), DpSize(300.dp, 200.dp))

    @Composable
    override fun Content(snap: WidgetSnapshot, style: WidgetStyle, prefs: Preferences) {
        val size = LocalSize.current
        val context = LocalContext.current
        val pad = 14.dp
        val header = if (size.height >= 130.dp) 42.dp else 0.dp
        val gridW = size.width - pad * 2
        val gridH = size.height - pad * 2 - header - if (header > 0.dp) 8.dp else 0.dp
        val cell = gridH.value / (7 + 6 * 0.22f)
        val weeks = ((gridW.value + cell * 0.22f) / (cell * 1.22f)).toInt().coerceIn(4, 53)

        val firstDay = snap.today.minusDays((snap.today.dayOfWeek.value - 1).toLong()).minusWeeks((weeks - 1).toLong())
        var clean = 0
        var slips = 0
        var d = firstDay
        while (!d.isAfter(snap.today)) {
            when (snap.status(d)) { DayStatus.CLEAN -> clean++; DayStatus.RELAPSE -> slips++; else -> {} }
            d = d.plusDays(1)
        }
        val tracked = clean + slips

        Column(GlanceModifier.fillMaxSize().padding(pad)) {
            if (header > 0.dp) {
                Row(GlanceModifier.fillMaxWidth().height(header), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        T(Format.days(snap.stats.currentDays), 17.sp, style.text, FontWeight.Bold)
                        T("current streak", 10.sp, style.soft)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        T(if (tracked == 0) "—" else "${clean * 100 / tracked}%", 17.sp, style.accent, FontWeight.Bold, align = TextAlign.End)
                        T("clean · $weeks weeks", 10.sp, style.soft, align = TextAlign.End)
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
            }
            Image(
                ImageProvider(
                    WidgetArt.heatmap(dpToPx(context, gridW), dpToPx(context, gridH), weeks, snap.today, snap::status, style.palette(context)),
                ),
                contentDescription = "$clean clean days and $slips slips in the last $weeks weeks",
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
        }
    }
}

/** 4 — Daily motivation. Tapping the message shows another; tapping the footer opens the app. */
class QuoteWidget : DaymarkGlanceWidget() {
    override val kind = WidgetKind.QUOTE
    override val sizes = setOf(DpSize(180.dp, 50.dp), DpSize(250.dp, 100.dp), DpSize(250.dp, 170.dp))

    @Composable
    override fun Content(snap: WidgetSnapshot, style: WidgetStyle, prefs: Preferences) {
        val size = LocalSize.current
        val offset = prefs[QuoteOffsetKey] ?: 0
        // Your own reasons when you've written some (and discreet mode is off); otherwise gentle general messages.
        val reasons = snap.visibleReasons
        val all = reasons.ifEmpty { Messages.motivational }
        val base = Math.floorMod(snap.today.toEpochDay(), all.size.toLong()).toInt()
        val message = all[(base + offset) % all.size]
        val compact = size.height < 100.dp
        Column(
            GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 16.dp)
                .clickable(actionRunCallback<NextQuoteAction>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(R.drawable.ic_w_quote, style.accent, 22.dp)
                    Spacer(GlanceModifier.width(6.dp))
                    T(if (reasons.isEmpty()) "TODAY'S REMINDER" else "YOUR REASON", 10.sp, style.soft, FontWeight.Medium)
                }
                Spacer(GlanceModifier.defaultWeight())
            }
            T(
                message,
                (if (compact) 14f else (size.height.value * 0.1f).coerceIn(15f, 20f)).sp,
                style.text, FontWeight.Medium, maxLines = if (compact) 2 else 4,
            )
            if (!compact) Spacer(GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.height(if (compact) 2.dp else 0.dp))
            Row(
                GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("Day ${snap.stats.currentDays}", 11.sp, style.accent, FontWeight.Bold)
                if (snap.habit.isNotBlank()) T("  ·  free from ${snap.habit.lowercase()}", 11.sp, style.soft, modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

/**
 * 5 — Quick actions: ride out an urge (always), plus journal and log on wider sizes.
 * Labels are deliberately neutral — nothing on the home screen says what you're tracking.
 */
class ActionsWidget : DaymarkGlanceWidget() {
    override val kind = WidgetKind.ACTIONS
    override val sizes = setOf(DpSize(50.dp, 50.dp), DpSize(100.dp, 100.dp), DpSize(170.dp, 70.dp), DpSize(250.dp, 70.dp), DpSize(250.dp, 110.dp))

    @Composable
    override fun Content(snap: WidgetSnapshot, style: WidgetStyle, prefs: Preferences) {
        val size = LocalSize.current
        val context = LocalContext.current
        val urge = startIntent(Routes.intent(context, Routes.URGE))
        if (size.width < 170.dp) {
            // 1×1 / 2×2: a single calm "urge" button.
            val big = size.height >= 100.dp
            Column(
                GlanceModifier.fillMaxSize().padding(if (big) 14.dp else 6.dp).clickable(urge),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(tileModifier(style, strong = true).size(if (big) 52.dp else 34.dp), contentAlignment = Alignment.Center) {
                    Icon(R.drawable.ic_w_shield, style.text, if (big) 26.dp else 18.dp, "Ride out an urge")
                }
                if (big) {
                    Spacer(GlanceModifier.height(8.dp))
                    T("Urge?", 15.sp, style.text, FontWeight.Bold, align = TextAlign.Center)
                    T("Ride it out", 11.sp, style.soft, align = TextAlign.Center)
                }
            }
            return
        }
        val tall = size.height >= 110.dp
        Row(GlanceModifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionTile(R.drawable.ic_w_shield, "Urge?", "Ride it out", style, urge, tall, strong = true)
            Spacer(GlanceModifier.width(8.dp))
            ActionTile(R.drawable.ic_w_journal, "Journal", "How's today?", style, startIntent(Routes.intent(context, Routes.JOURNAL)), tall)
            if (size.width >= 250.dp) {
                Spacer(GlanceModifier.width(8.dp))
                ActionTile(R.drawable.ic_w_add, if (snap.isReduce) "Log use" else "Log", "Private", style, startIntent(Routes.intent(context, Routes.SLIP)), tall)
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.ActionTile(
        icon: Int, title: String, sub: String, style: WidgetStyle, action: androidx.glance.action.Action, tall: Boolean, strong: Boolean = false,
    ) {
        Column(
            tileModifier(style, strong).defaultWeight().fillMaxHeight().clickable(action).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, style.text, if (tall) 24.dp else 20.dp, title)
            Spacer(GlanceModifier.height(4.dp))
            T(title, 13.sp, style.text, FontWeight.Bold, align = TextAlign.Center)
            if (tall) T(sub, 10.sp, style.soft, align = TextAlign.Center)
        }
    }
}

/** 6 — Your progress: money kept, urges ridden out, clean days, best streak, and today's count in cut-down mode. */
class StatsWidget : DaymarkGlanceWidget() {
    override val kind = WidgetKind.STATS
    override val sizes = setOf(DpSize(110.dp, 50.dp), DpSize(250.dp, 50.dp), DpSize(150.dp, 150.dp), DpSize(250.dp, 150.dp), DpSize(250.dp, 230.dp))

    private data class Stat(val icon: Int, val value: String, val label: String)

    @Composable
    override fun Content(snap: WidgetSnapshot, style: WidgetStyle, prefs: Preferences) {
        val size = LocalSize.current
        val stats = buildList {
            if (snap.tracksMoney) add(Stat(R.drawable.ic_w_wallet, Format.money(snap.moneyKept), "kept"))
            add(Stat(R.drawable.ic_w_shield, "${snap.urgeCount}", if (snap.urgeCount == 1) "urge ridden out" else "urges ridden out"))
            if (snap.isReduce) add(Stat(R.drawable.ic_w_today, "${snap.todayUses} of ${snap.dailyLimit}", "today"))
            add(Stat(R.drawable.ic_w_leaf, "${snap.stats.totalCleanDays}", if (snap.isReduce) "days in limit" else "clean days"))
            add(Stat(R.drawable.ic_w_trophy, "${snap.stats.longestDays}d", "best streak"))
        }
        if (size.height < 100.dp) {
            // Slim: one or two stats side by side.
            Row(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                stats.take(if (size.width >= 250.dp) 2 else 1).forEachIndexed { i, st ->
                    if (i > 0) Spacer(GlanceModifier.width(12.dp))
                    Row(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(st.icon, style.accent, 18.dp)
                        Spacer(GlanceModifier.width(8.dp))
                        Column {
                            T(st.value, 17.sp, style.text, FontWeight.Bold)
                            T(st.label, 10.sp, style.soft)
                        }
                    }
                }
            }
            return
        }
        val cols = if (size.width >= 250.dp) 2 else 1
        val rows = if (size.height >= 230.dp) 3 else 2
        Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
            HeaderPill(snap, style)
            Spacer(GlanceModifier.height(8.dp))
            stats.take(cols * rows).chunked(cols).forEachIndexed { r, row ->
                if (r > 0) Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    row.forEachIndexed { c, st ->
                        if (c > 0) Spacer(GlanceModifier.width(8.dp))
                        Column(
                            tileModifier(style).defaultWeight().fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(st.icon, style.accent, 16.dp)
                            Spacer(GlanceModifier.height(4.dp))
                            T(st.value, 18.sp, style.text, FontWeight.Bold)
                            T(st.label, 10.sp, style.soft)
                        }
                    }
                }
            }
        }
    }
}

class NextQuoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[QuoteOffsetKey] = ((it[QuoteOffsetKey] ?: 0) + 1) % 10_000 }
        QuoteWidget().update(context, glanceId)
    }
}

// region Receivers
/** Also refreshes on clock/timezone/date changes so day counts are never stale. */
abstract class DaymarkWidgetReceiverBase : GlanceAppWidgetReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try { WidgetUpdater.updateAll(context.applicationContext) } finally { pending.finish() }
                }
            }
        }
    }
}

/** Keeps its original name so widgets placed with v1.0 survive the update. */
class DaymarkWidgetReceiver : DaymarkWidgetReceiverBase() { override val glanceAppWidget: GlanceAppWidget = StreakWidget() }
class RingWidgetReceiver : DaymarkWidgetReceiverBase() { override val glanceAppWidget: GlanceAppWidget = RingWidget() }
class HeatmapWidgetReceiver : DaymarkWidgetReceiverBase() { override val glanceAppWidget: GlanceAppWidget = HeatmapWidget() }
class QuoteWidgetReceiver : DaymarkWidgetReceiverBase() { override val glanceAppWidget: GlanceAppWidget = QuoteWidget() }
class ActionsWidgetReceiver : DaymarkWidgetReceiverBase() { override val glanceAppWidget: GlanceAppWidget = ActionsWidget() }
class StatsWidgetReceiver : DaymarkWidgetReceiverBase() { override val glanceAppWidget: GlanceAppWidget = StatsWidget() }

fun WidgetKind.receiver(): Class<out GlanceAppWidgetReceiver> = when (this) {
    WidgetKind.STREAK -> DaymarkWidgetReceiver::class.java
    WidgetKind.RING -> RingWidgetReceiver::class.java
    WidgetKind.HEATMAP -> HeatmapWidgetReceiver::class.java
    WidgetKind.QUOTE -> QuoteWidgetReceiver::class.java
    WidgetKind.ACTIONS -> ActionsWidgetReceiver::class.java
    WidgetKind.STATS -> StatsWidgetReceiver::class.java
}

fun WidgetKind.widget(): GlanceAppWidget = when (this) {
    WidgetKind.STREAK -> StreakWidget()
    WidgetKind.RING -> RingWidget()
    WidgetKind.HEATMAP -> HeatmapWidget()
    WidgetKind.QUOTE -> QuoteWidget()
    WidgetKind.ACTIONS -> ActionsWidget()
    WidgetKind.STATS -> StatsWidget()
}

fun kindForProvider(className: String?): WidgetKind =
    WidgetKind.entries.firstOrNull { it.receiver().name == className } ?: WidgetKind.STREAK
// endregion
