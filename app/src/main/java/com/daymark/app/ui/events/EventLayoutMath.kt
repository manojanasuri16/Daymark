package com.daymark.app.ui.events

import com.daymark.app.domain.ItemSize
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure layout maths for [EventArt]; no Android types so it is unit-tested on the JVM. */
object EventLayoutMath {
    enum class Aspect { WIDE, SQUARE, TALL }

    /** Wide cards (2x1, 4x2) put things side by side; square and tall ones stack. */
    fun aspectOf(w: Float, h: Float): Aspect = when {
        w / h >= 1.9f -> Aspect.WIDE
        h / w >= 1.25f -> Aspect.TALL
        else -> Aspect.SQUARE
    }

    /** (startAngle, sweep) in degrees for a ring starting at 12 o'clock. A tiny minimum keeps the round cap visible at 0%. */
    fun ringArc(fraction: Float, clockwise: Boolean): Pair<Float, Float> {
        val f = fraction.coerceIn(0f, 1f)
        val sweep = max(f * 360f, 0.01f)
        return -90f to (if (clockwise) sweep else -sweep)
    }

    data class GridSpec(val cols: Int, val rows: Int, val cell: Float, val gap: Float)

    /** The column count that makes square cells as large as possible inside w x h. */
    fun bestGrid(w: Float, h: Float, count: Int, gap: Float): GridSpec {
        val n = max(count, 1)
        var best = GridSpec(n, 1, 1f, gap)
        var bestCell = -1f
        for (cols in 1..n) {
            val rows = ceil(n / cols.toDouble()).toInt()
            val cw = (w - (cols - 1) * gap) / cols
            val ch = (h - (rows - 1) * gap) / rows
            val cell = min(cw, ch)
            if (cell > bestCell + 1e-4f) { bestCell = cell; best = GridSpec(cols, rows, max(cell, 1f), gap) }
        }
        return best
    }

    private const val MIN = 60_000L
    private val UNITS = longArrayOf(MIN, 60 * MIN, 24 * 60 * MIN, 7 * 24 * 60 * MIN, 30 * 24 * 60 * MIN, 365 * 24 * 60 * MIN)

    /** Fixed item counts when items do not match time units. */
    fun fixedCount(size: ItemSize, bars: Boolean): Int = when (size) {
        ItemSize.SMALL -> if (bars) 40 else 120
        ItemSize.MEDIUM -> if (bars) 24 else 60
        ItemSize.LARGE -> if (bars) 12 else 24
    }

    /**
     * Number of items. With [matchItems] it is one per time unit of the whole span: the smallest unit
     * (minute..year) that gives at most [maxCount] items. [maxCount] comes from how many fit on the card.
     */
    fun itemCount(totalMs: Long?, matchItems: Boolean, size: ItemSize, bars: Boolean, maxCount: Int): Int {
        val cap = max(maxCount, 1)
        if (!matchItems || totalMs == null || totalMs <= 0) return min(fixedCount(size, bars), cap)
        for (u in UNITS) {
            val n = ceil(totalMs / u.toDouble()).toInt()
            if (n <= cap) return max(n, 1)
        }
        return cap
    }

    /** Items drawn as elapsed. Whole 100% is always all of them. */
    fun filledCount(fraction: Float?, count: Int): Int {
        val f = (fraction ?: 1f).coerceIn(0f, 1f)
        return if (f >= 1f) count else floor(f * count + 1e-4f).roundToInt().coerceIn(0, count)
    }

    /** Whether item [i] is elapsed; the counter-clockwise ("reversed") direction fills from the end. */
    fun isFilled(i: Int, count: Int, filled: Int, clockwise: Boolean): Boolean =
        if (clockwise) i < filled else i >= count - filled

    /** Largest size in [minSize, maxSize] for which [fits] is true (binary search); [minSize] if none fits. */
    fun fitSize(minSize: Float, maxSize: Float, fits: (Float) -> Boolean): Float {
        if (maxSize <= minSize || fits(maxSize)) return max(maxSize, minSize)
        var lo = minSize
        var hi = maxSize
        repeat(10) {
            val mid = (lo + hi) / 2f
            if (fits(mid)) lo = mid else hi = mid
        }
        return if (abs(lo) < minSize) minSize else lo
    }
}
