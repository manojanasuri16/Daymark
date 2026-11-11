package com.daymark.app.domain

import java.time.ZoneId

/** Pure helpers for widget refresh timing and the Glance `RefreshKey`. */
object WidgetRefresh {
    /** Never wake more often than this (no exact alarms). */
    const val MIN_DELAY_MS = 15_000L

    /**
     * A strictly increasing refresh stamp. Written into a widget's Glance state before each update so a still-running
     * Glance session sees changed input and recomposes (and recomputes its snapshot from the current clock).
     */
    fun nextKey(previous: Long?, now: Long): Long = if (previous == null || now > previous) now else previous + 1

    /**
     * The absolute time of the next event-widget refresh: the earliest of the next local midnight, the end of the
     * current cycle of any ongoing event, and `now + maxIntervalMinutes` (the "refresh rate" setting, 15 or 30), never
     * sooner than [MIN_DELAY_MS] from [now].
     */
    fun nextEventRefresh(events: List<CountEvent>, now: Long, maxIntervalMinutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val cap = now + (if (maxIntervalMinutes >= 30) 30 else 15) * 60_000L
        val midnight = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        var next = minOf(cap, midnight)
        for (e in events) {
            if (e.archived) continue
            val end = EventEngine.progress(e, now, zone).end ?: continue
            if (end > now) next = minOf(next, end)
        }
        return maxOf(next, now + MIN_DELAY_MS)
    }
}
