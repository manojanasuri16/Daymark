package com.daymark.app.widget

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.daymark.app.R

/** Visual styles a user can pick per widget. Colours live in res/values(-night)/widget_colors.xml. */
enum class WidgetStyle(
    val id: String,
    val label: String,
    @DrawableRes val background: Int,
    @ColorRes val start: Int,
    @ColorRes val center: Int,
    @ColorRes val end: Int,
    @ColorRes val text: Int,
    @ColorRes val soft: Int,
    @ColorRes val accent: Int,
    @ColorRes val track: Int,
    @ColorRes val clean: Int,
    @ColorRes val relapse: Int,
    @ColorRes val untracked: Int,
) {
    DUSK("dusk", "Dusk", R.drawable.widget_bg_dusk,
        R.color.wdg_dusk_start, R.color.wdg_dusk_center, R.color.wdg_dusk_end, R.color.wdg_dusk_text, R.color.wdg_dusk_soft,
        R.color.wdg_dusk_accent, R.color.wdg_dusk_track, R.color.wdg_dusk_clean, R.color.wdg_dusk_relapse, R.color.wdg_dusk_untracked),
    DAWN("dawn", "Dawn", R.drawable.widget_bg_dawn,
        R.color.wdg_dawn_start, R.color.wdg_dawn_center, R.color.wdg_dawn_end, R.color.wdg_dawn_text, R.color.wdg_dawn_soft,
        R.color.wdg_dawn_accent, R.color.wdg_dawn_track, R.color.wdg_dawn_clean, R.color.wdg_dawn_relapse, R.color.wdg_dawn_untracked),
    FOREST("forest", "Forest", R.drawable.widget_bg_forest,
        R.color.wdg_forest_start, R.color.wdg_forest_center, R.color.wdg_forest_end, R.color.wdg_forest_text, R.color.wdg_forest_soft,
        R.color.wdg_forest_accent, R.color.wdg_forest_track, R.color.wdg_forest_clean, R.color.wdg_forest_relapse, R.color.wdg_forest_untracked),
    MIDNIGHT("midnight", "Midnight", R.drawable.widget_bg_midnight,
        R.color.wdg_midnight_start, R.color.wdg_midnight_center, R.color.wdg_midnight_end, R.color.wdg_midnight_text, R.color.wdg_midnight_soft,
        R.color.wdg_midnight_accent, R.color.wdg_midnight_track, R.color.wdg_midnight_clean, R.color.wdg_midnight_relapse, R.color.wdg_midnight_untracked),
    SUNRISE("sunrise", "Sunrise", R.drawable.widget_bg_sunrise,
        R.color.wdg_sunrise_start, R.color.wdg_sunrise_center, R.color.wdg_sunrise_end, R.color.wdg_sunrise_text, R.color.wdg_sunrise_soft,
        R.color.wdg_sunrise_accent, R.color.wdg_sunrise_track, R.color.wdg_sunrise_clean, R.color.wdg_sunrise_relapse, R.color.wdg_sunrise_untracked),
    MINIMAL("minimal", "Minimal", R.drawable.widget_bg_minimal,
        R.color.wdg_minimal_start, R.color.wdg_minimal_center, R.color.wdg_minimal_end, R.color.wdg_minimal_text, R.color.wdg_minimal_soft,
        R.color.wdg_minimal_accent, R.color.wdg_minimal_track, R.color.wdg_minimal_clean, R.color.wdg_minimal_relapse, R.color.wdg_minimal_untracked);

    /** Resolved ARGB colours for bitmap rendering (respects the context's night mode for Minimal). */
    fun palette(context: Context) = Palette(
        text = context.getColor(text),
        soft = context.getColor(soft),
        accent = context.getColor(accent),
        track = context.getColor(track),
        clean = context.getColor(clean),
        relapse = context.getColor(relapse),
        untracked = context.getColor(untracked),
    )

    data class Palette(
        val text: Int, val soft: Int, val accent: Int, val track: Int,
        val clean: Int, val relapse: Int, val untracked: Int,
    )

    companion object {
        val DEFAULT = DUSK
        fun of(id: String?): WidgetStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** The widget families Daymark offers. */
enum class WidgetKind(val title: String, val blurb: String) {
    STREAK("Streak", "Live counter with a daily message"),
    RING("Progress ring", "Fills toward your next milestone"),
    HEATMAP("Heatmap", "Your recent weeks at a glance"),
    QUOTE("Daily motivation", "A gentle message, or your own reasons — tap for another"),
    ACTIONS("Quick actions", "Ride out an urge or journal in one tap"),
    STATS("Your progress", "Money kept, urges ridden out and more"),
    EVENT("Event", "Countdown or time-since card in the design you made. Added from here it shows the event nearest to finish"),
}

/** Style that newly placed widgets of a kind adopt (set from the in-app gallery right before pinning). */
object WidgetDefaults {
    private const val FILE = "widget_defaults"
    fun get(context: Context, kind: WidgetKind): WidgetStyle =
        WidgetStyle.of(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(kind.name, null))
    fun set(context: Context, kind: WidgetKind, style: WidgetStyle) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(kind.name, style.id).apply()
    }
    /** Tracker that newly placed widgets of a kind show; 0 = whichever is selected in the app. */
    fun habit(context: Context, kind: WidgetKind): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong("${kind.name}_habit", 0L)
    fun setHabit(context: Context, kind: WidgetKind, habitId: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong("${kind.name}_habit", habitId).apply()
    }
}
