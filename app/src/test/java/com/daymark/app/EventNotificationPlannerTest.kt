package com.daymark.app

import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventKind
import com.daymark.app.domain.EventNotificationPlanner
import com.daymark.app.domain.EventOptions
import com.daymark.app.domain.EventRepeat
import com.daymark.app.domain.NotifType
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventNotificationPlannerTest {
    private val utc = ZoneId.of("UTC")
    private fun ms(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, utc).toInstant().toEpochMilli()
    private val all: (NotifType) -> Boolean = { true }
    private val none: (NotifType) -> Boolean = { false }

    private fun countdown(id: Long, name: String, start: Long, end: Long, repeat: EventRepeat = EventRepeat.NONE, archived: Boolean = false) =
        CountEvent(id = id, name = name, kind = EventKind.COUNTDOWN, startAt = start, endAt = end, repeat = repeat, archived = archived)

    private fun since(id: Long, name: String, start: Long, o: EventOptions = EventOptions()) =
        CountEvent(id = id, name = name, kind = EventKind.SINCE, startAt = start, options = o)

    @Test fun `all switches off plans nothing`() {
        val e = countdown(1, "Trip", ms(2026, 1, 1), ms(2026, 12, 1))
        assertNull(EventNotificationPlanner.nextTime(listOf(e), ms(2026, 5, 1), none, zone = utc))
    }

    @Test fun `picks the earliest across events`() {
        val a = countdown(1, "A", ms(2026, 1, 1), ms(2026, 12, 20))
        val b = countdown(2, "B", ms(2026, 1, 1), ms(2026, 12, 10))
        val c = EventNotificationPlanner.candidates(listOf(a, b), ms(2026, 5, 1), all, zone = utc)
        assertEquals(listOf(2L, 2L, 2L, 1L, 1L, 1L), c.map { it.eventId })
        // earliest is B's one-week-before (3 Dec)
        assertEquals(ms(2026, 12, 3), c.first().at)
        assertEquals(2L, c.first().eventId)
        assertEquals(NotifType.ONE_WEEK_BEFORE, c.first().type)
    }

    @Test fun `disabled types are skipped`() {
        val e = countdown(1, "Trip", ms(2026, 1, 1), ms(2026, 12, 20))
        val only: (NotifType) -> Boolean = { it == NotifType.COMPLETED }
        val c = EventNotificationPlanner.candidates(listOf(e), ms(2026, 5, 1), only, zone = utc)
        assertEquals(listOf(NotifType.COMPLETED), c.map { it.type })
        assertEquals(ms(2026, 12, 20), c[0].at)
    }

    @Test fun `archived events never notify`() {
        val e = countdown(1, "Trip", ms(2026, 1, 1), ms(2026, 12, 20), archived = true)
        assertTrue(EventNotificationPlanner.candidates(listOf(e), ms(2026, 5, 1), all, zone = utc).isEmpty())
    }

    @Test fun `just completed within an hour is still due, older is dropped`() {
        val e = countdown(1, "Trip", ms(2026, 1, 1), ms(2026, 5, 1, 12, 0))
        val only: (NotifType) -> Boolean = { it == NotifType.COMPLETED }
        val recent = EventNotificationPlanner.due(listOf(e), ms(2026, 5, 1, 12, 30), only, zone = utc)
        assertEquals(1, recent.size)
        val old = EventNotificationPlanner.due(listOf(e), ms(2026, 5, 1, 14, 0), only, zone = utc)
        assertTrue(old.isEmpty())
        assertNull(EventNotificationPlanner.nextTime(listOf(e), ms(2026, 5, 1, 14, 0), only, zone = utc))
    }

    @Test fun `already sent is not repeated`() {
        val e = countdown(1, "Trip", ms(2026, 1, 1), ms(2026, 5, 1, 12, 0))
        val only: (NotifType) -> Boolean = { it == NotifType.COMPLETED }
        val now = ms(2026, 5, 1, 12, 5)
        val first = EventNotificationPlanner.due(listOf(e), now, only, zone = utc)
        val again = EventNotificationPlanner.due(listOf(e), now, only, { k -> first.any { it.key == k } }, zone = utc)
        assertTrue(again.isEmpty())
    }

    @Test fun `due now gives now as next time`() {
        val e = countdown(1, "Trip", ms(2026, 1, 1), ms(2026, 5, 1, 12, 0))
        val now = ms(2026, 5, 1, 12, 10)
        assertEquals(now, EventNotificationPlanner.nextTime(listOf(e), now, all, zone = utc))
    }

    @Test fun `repeating yearly event plans the next cycle after completion`() {
        val e = countdown(1, "Birthday", ms(2020, 6, 1), ms(2020, 6, 2), EventRepeat.YEAR)
        val only: (NotifType) -> Boolean = { it == NotifType.ONE_DAY_BEFORE }
        val c = EventNotificationPlanner.candidates(listOf(e), ms(2026, 7, 1), only, zone = utc)
        assertEquals(ms(2027, 6, 1), c.first().at)
        assertEquals("Birthday is tomorrow", EventNotificationPlanner.text(e, c.first(), true, utc).body)
    }

    @Test fun `count up every day after start`() {
        val e = since(1, "Quit", ms(2026, 5, 1, 8, 0))
        val only: (NotifType) -> Boolean = { it == NotifType.DAILY_AFTER }
        val now = ms(2026, 5, 10, 9, 0)
        val c = EventNotificationPlanner.candidates(listOf(e), now, only, zone = utc)
        assertEquals(ms(2026, 5, 11, 8, 0), c.first().at)
        assertEquals("Quit: 10 days so far", EventNotificationPlanner.text(e, c.first(), true, utc).body)
    }

    @Test fun `weekly milestone wins over daily at the same moment`() {
        val e = since(1, "Quit", ms(2026, 5, 1, 8, 0))
        val now = ms(2026, 5, 7, 9, 0)
        val c = EventNotificationPlanner.candidates(listOf(e), now, all, zone = utc)
        assertEquals(ms(2026, 5, 8, 8, 0), c.first().at)
        assertEquals(NotifType.WEEKLY_AFTER, c.first().type)
        assertEquals("Quit: 1 week so far", EventNotificationPlanner.text(e, c.first(), true, utc).body)
    }

    @Test fun `monthly and yearly after start`() {
        val e = since(1, "Move", ms(2025, 1, 15, 10, 0))
        val m: (NotifType) -> Boolean = { it == NotifType.MONTHLY_AFTER }
        val y: (NotifType) -> Boolean = { it == NotifType.YEARLY_AFTER }
        val now = ms(2026, 3, 1)
        assertEquals(ms(2026, 3, 15, 10, 0), EventNotificationPlanner.nextTime(listOf(e), now, m, zone = utc))
        assertEquals(ms(2027, 1, 15, 10, 0), EventNotificationPlanner.nextTime(listOf(e), now, y, zone = utc))
    }

    @Test fun `wording honours showNameOutside`() {
        val e = countdown(1, "Sam's birthday", ms(2026, 1, 1), ms(2026, 6, 1))
        val done = com.daymark.app.domain.PlannedNotification(1, NotifType.COMPLETED, ms(2026, 6, 1))
        assertEquals("🎉 Sam's birthday is complete", EventNotificationPlanner.text(e, done, true, utc).body)
        val generic = EventNotificationPlanner.text(e, done, false, utc)
        assertEquals("Daymark", generic.title)
        assertTrue(!generic.body.contains("Sam"))
        val week = com.daymark.app.domain.PlannedNotification(1, NotifType.ONE_WEEK_BEFORE, ms(2026, 5, 25))
        assertEquals("Sam's birthday is in one week", EventNotificationPlanner.text(e, week, true, utc).body)
    }
}
