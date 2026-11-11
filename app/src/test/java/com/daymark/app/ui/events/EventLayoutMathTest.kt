package com.daymark.app.ui.events

import com.daymark.app.domain.ItemSize
import com.daymark.app.ui.events.EventLayoutMath.Aspect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventLayoutMathTest {
    private val day = 24 * 60 * 60_000L

    @Test fun aspects() {
        assertEquals(Aspect.WIDE, EventLayoutMath.aspectOf(160f, 70f))
        assertEquals(Aspect.SQUARE, EventLayoutMath.aspectOf(160f, 160f))
        assertEquals(Aspect.SQUARE, EventLayoutMath.aspectOf(320f, 220f))
        assertEquals(Aspect.TALL, EventLayoutMath.aspectOf(100f, 160f))
    }

    @Test fun ringSweepDirection() {
        assertEquals(-90f to 180f, EventLayoutMath.ringArc(0.5f, true))
        assertEquals(-90f to -180f, EventLayoutMath.ringArc(0.5f, false))
        assertEquals(360f, EventLayoutMath.ringArc(1f, true).second, 0.001f)
        assertTrue(EventLayoutMath.ringArc(0f, true).second > 0f)
        assertEquals(360f, EventLayoutMath.ringArc(7f, true).second, 0.001f)
    }

    @Test fun bestGridFillsArea() {
        val g = EventLayoutMath.bestGrid(300f, 100f, 30, 0f)
        assertEquals(10, g.cols); assertEquals(3, g.rows)
        assertEquals(30f, g.cell, 0.001f)
        val tall = EventLayoutMath.bestGrid(100f, 300f, 30, 0f)
        assertEquals(3, tall.cols); assertEquals(10, tall.rows)
        assertTrue(g.cols * g.rows >= 30)
        assertEquals(1, EventLayoutMath.bestGrid(50f, 50f, 1, 4f).cols)
    }

    @Test fun matchItemsPicksSmallestUnitThatFits() {
        // 30 days, cap 120 -> one per day
        assertEquals(30, EventLayoutMath.itemCount(30 * day, true, ItemSize.MEDIUM, false, 120))
        // 1 year, cap 120 -> weeks (52)
        assertEquals(53, EventLayoutMath.itemCount(365 * day, true, ItemSize.MEDIUM, false, 120))
        // 4 hours, cap 120 -> hours (4)
        assertEquals(4, EventLayoutMath.itemCount(4 * 60 * 60_000L, true, ItemSize.MEDIUM, false, 120))
        // tiny cap falls back to a bigger unit but never exceeds it
        assertTrue(EventLayoutMath.itemCount(30 * day, true, ItemSize.MEDIUM, true, 10) <= 10)
        assertEquals(1, EventLayoutMath.itemCount(60_000L, true, ItemSize.SMALL, false, 5))
    }

    @Test fun fixedCountsAndNoTotal() {
        assertEquals(60, EventLayoutMath.itemCount(null, true, ItemSize.MEDIUM, false, 500))
        assertEquals(24, EventLayoutMath.itemCount(100 * day, false, ItemSize.MEDIUM, true, 500))
        assertEquals(12, EventLayoutMath.itemCount(100 * day, false, ItemSize.LARGE, true, 500))
        assertEquals(10, EventLayoutMath.itemCount(100 * day, false, ItemSize.LARGE, true, 10))
    }

    @Test fun filledAndDirection() {
        assertEquals(5, EventLayoutMath.filledCount(0.5f, 10))
        assertEquals(10, EventLayoutMath.filledCount(1f, 10))
        assertEquals(10, EventLayoutMath.filledCount(null, 10))
        assertEquals(0, EventLayoutMath.filledCount(0f, 10))
        assertEquals(9, EventLayoutMath.filledCount(0.99f, 10))
        assertTrue(EventLayoutMath.isFilled(0, 10, 3, true))
        assertTrue(!EventLayoutMath.isFilled(3, 10, 3, true))
        assertTrue(EventLayoutMath.isFilled(9, 10, 3, false))
        assertTrue(!EventLayoutMath.isFilled(6, 10, 3, false))
    }

    @Test fun fitSizeBinarySearch() {
        assertEquals(40f, EventLayoutMath.fitSize(10f, 40f) { true }, 0.001f)
        assertEquals(10f, EventLayoutMath.fitSize(10f, 40f) { false }, 0.001f)
        val s = EventLayoutMath.fitSize(10f, 40f) { it <= 25f }
        assertEquals(25f, s, 0.1f)
        assertTrue(s <= 25f)
    }
}
