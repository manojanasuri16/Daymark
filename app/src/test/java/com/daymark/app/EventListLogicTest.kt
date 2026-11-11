package com.daymark.app

import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventFilter
import com.daymark.app.domain.EventKind
import com.daymark.app.domain.EventListLogic
import com.daymark.app.domain.EventRepeat
import com.daymark.app.domain.EventSort
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EventListLogicTest {
    private val zone = ZoneId.of("UTC")
    private val now = 1_000_000_000L
    private val day = 86_400_000L

    private fun ev(id: Long, name: String, start: Long, end: Long?, kind: EventKind = EventKind.COUNTDOWN,
                   repeat: EventRepeat = EventRepeat.NONE, archived: Boolean = false, created: Long = id, modified: Long = id) =
        CountEvent(id = id, name = name, kind = kind, startAt = start, endAt = end, allDay = false, repeat = repeat,
            archived = archived, createdAt = created, modifiedAt = modified)

    private val ongoing = ev(1, "Beta", now - day, now + 2 * day)
    private val done = ev(2, "alpha", now - 5 * day, now - day)
    private val open = ev(3, "Open", now - day, null, EventKind.SINCE)
    private val repeating = ev(4, "Rep", now - 3 * day, now - day, repeat = EventRepeat.DAY)
    private val arch = ev(5, "Old", now - day, now + day, archived = true)
    private val all = listOf(ongoing, done, open, repeating, arch)

    private fun ids(l: List<CountEvent>) = l.map { it.id }

    @Test fun filters() {
        assertEquals(listOf(1L, 3L, 4L), ids(EventListLogic.filter(all, EventFilter.ONGOING, now, zone)))
        assertEquals(listOf(2L), ids(EventListLogic.filter(all, EventFilter.COMPLETED, now, zone)))
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(EventListLogic.filter(all, EventFilter.ALL, now, zone)))
        assertEquals(listOf(5L), ids(EventListLogic.filter(all, EventFilter.ARCHIVED, now, zone)))
    }

    @Test fun counts() {
        val c = EventListLogic.counts(all, now, zone)
        assertEquals(3, c[EventFilter.ONGOING]); assertEquals(1, c[EventFilter.COMPLETED])
        assertEquals(4, c[EventFilter.ALL]); assertEquals(1, c[EventFilter.ARCHIVED])
    }

    @Test fun sortsByNameIgnoringCase() {
        assertEquals(listOf(2L, 1L), ids(EventListLogic.sort(listOf(ongoing, done), EventSort.NAME, true, now, zone)))
        assertEquals(listOf(1L, 2L), ids(EventListLogic.sort(listOf(ongoing, done), EventSort.NAME, false, now, zone)))
    }

    @Test fun sortsByDates() {
        val l = listOf(ongoing, done, open)
        assertEquals(listOf(3L, 2L, 1L), ids(EventListLogic.sort(l, EventSort.CREATED, false, now, zone)))
        assertEquals(listOf(1L, 2L, 3L), ids(EventListLogic.sort(l, EventSort.MODIFIED, true, now, zone)))
    }

    @Test fun upcomingPutsEndlessLast() {
        val soon = ev(6, "Soon", now - day, now + day)
        val l = listOf(open, ongoing, done, soon)
        assertEquals(listOf(6L, 1L), ids(EventListLogic.sort(l, EventSort.UPCOMING, true, now, zone)).take(2))
        assertEquals(listOf(1L, 6L), ids(EventListLogic.sort(l, EventSort.UPCOMING, false, now, zone)).take(2))
        assertEquals(setOf(2L, 3L), ids(EventListLogic.sort(l, EventSort.UPCOMING, false, now, zone)).takeLast(2).toSet())
    }

    @Test fun sortsByPercent() {
        val l = listOf(ongoing, done)
        assertEquals(listOf(1L, 2L), ids(EventListLogic.sort(l, EventSort.PERCENT, true, now, zone)))
        assertEquals(listOf(2L, 1L), ids(EventListLogic.sort(l, EventSort.PERCENT, false, now, zone)))
    }
}
