package com.daymark.app

import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventKind
import com.daymark.app.domain.WidgetRefresh
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import org.junit.Assert.assertEquals

class WidgetRefreshTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(h: Int, m: Int = 0) = ZonedDateTime.of(2026, 5, 10, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private fun countdown(end: Long, now: Long) = CountEvent(id = 1, name = "x", kind = EventKind.COUNTDOWN, startAt = now - 86_400_000L, endAt = end)

    @Test fun keyIsStrictlyIncreasing() {
        assertEquals(100L, WidgetRefresh.nextKey(null, 100))
        assertEquals(101L, WidgetRefresh.nextKey(100, 100))
        assertEquals(101L, WidgetRefresh.nextKey(100, 50))
        assertEquals(200L, WidgetRefresh.nextKey(100, 200))
    }

    @Test fun cappedByRefreshSetting() {
        val now = ms(10)
        assertEquals(now + 15 * 60_000L, WidgetRefresh.nextEventRefresh(emptyList(), now, 15, zone))
        assertEquals(now + 30 * 60_000L, WidgetRefresh.nextEventRefresh(emptyList(), now, 30, zone))
        assertEquals(now + 15 * 60_000L, WidgetRefresh.nextEventRefresh(emptyList(), now, 7, zone))
    }

    @Test fun midnightAndCompletionBoundaries() {
        val now = ms(23, 50)
        assertEquals(ms(0).plus(86_400_000L), WidgetRefresh.nextEventRefresh(emptyList(), now, 30, zone))
        val end = now + 5 * 60_000L
        assertEquals(end, WidgetRefresh.nextEventRefresh(listOf(countdown(end, now)), now, 30, zone))
    }

    @Test fun neverSoonerThanFifteenSeconds() {
        val now = ms(10)
        assertEquals(now + 15_000L, WidgetRefresh.nextEventRefresh(listOf(countdown(now + 1_000L, now)), now, 15, zone))
    }
}
