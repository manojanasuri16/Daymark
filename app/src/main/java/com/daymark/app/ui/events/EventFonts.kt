package com.daymark.app.ui.events

import android.content.Context
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

data class FontInfo(val id: String, val label: String, val preview: String)

/**
 * Fonts of the Events cards. TTFs are bundled in assets/fonts (see third_party/FONTS.md); a family that fails to load
 * falls back to the closest system family so a card always renders.
 */
object EventFonts {
    const val DEFAULT_ID = "inter"

    val all: List<FontInfo> = listOf(
        FontInfo("inter", "Inter", "Aa 123"),
        FontInfo("quicksand", "Quicksand", "Aa 123"),
        FontInfo("roboto-condensed", "Condensed", "Aa 123"),
        FontInfo("roboto-mono", "Mono", "Aa 123"),
        FontInfo("roboto-slab", "Slab", "Aa 123"),
        FontInfo("roboto-wide", "Wide", "Aa 123"),
        FontInfo("baskerville", "Baskerville", "Aa 123"),
        FontInfo("gloock", "Gloock", "Aa 123"),
        FontInfo("merienda", "Merienda", "Aa 123"),
        FontInfo("marker", "Marker", "Aa 123"),
    )

    private class Spec(val asset: String, val fallback: String, val variable: Boolean)

    private val specs = mapOf(
        "inter" to Spec("inter", "sans-serif", true),
        "quicksand" to Spec("quicksand", "sans-serif-rounded", true),
        "roboto-condensed" to Spec("roboto_condensed", "sans-serif-condensed", true),
        "roboto-mono" to Spec("roboto_mono", "monospace", true),
        "roboto-slab" to Spec("roboto_slab", "serif", true),
        "roboto-wide" to Spec("roboto_wide", "sans-serif", true),
        "baskerville" to Spec("baskerville", "serif", true),
        "gloock" to Spec("gloock", "serif", false),
        "merienda" to Spec("merienda", "cursive", true),
        "marker" to Spec("marker", "casual", false),
    )

    private val cache = ConcurrentHashMap<String, Typeface>()

    fun typeface(context: Context, id: String, bold: Boolean): Typeface = weighted(context, id, if (bold) 700 else 400)

    /** [weight] 100..900; only variable families react, the rest return their one weight. */
    fun weighted(context: Context, id: String, weight: Int): Typeface {
        val spec = specs[id] ?: specs.getValue(DEFAULT_ID)
        val w = if (spec.variable) weight.coerceIn(100, 900) else 400
        return cache.getOrPut("${spec.asset}:$w") { load(context.applicationContext, spec, w) }
    }

    private fun load(context: Context, spec: Spec, weight: Int): Typeface {
        val built = runCatching {
            val b = Typeface.Builder(context.assets, "fonts/${spec.asset}.ttf")
            if (spec.variable) b.setFontVariationSettings("'wght' $weight")
            b.setWeight(weight).build()
        }.getOrNull()
        return built ?: Typeface.create(spec.fallback, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
}
