package com.daymark.app

import com.daymark.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EventEditingTest {
    private val utc = ZoneId.of("UTC")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 0) = LocalDate.of(y, m, d).atTime(h, 0).atZone(utc).toInstant().toEpochMilli()
    private fun ev(kind: EventKind, start: Long, end: Long?, o: EventOptions = EventOptions(), name: String = "x") =
        CountEvent(name = name, kind = kind, startAt = start, endAt = end, options = o)

    @Test fun `duration validation`() {
        assertTrue(EventEditing.durationValid(EventOptions(timerDays = 7)))
        assertFalse(EventEditing.durationValid(EventOptions(timerDays = 0)))
        assertFalse(EventEditing.durationValid(EventOptions(timerDays = 0, timerYears = 101)))
        assertTrue(EventEditing.durationValid(EventOptions(timerDays = 0, timerMinutes = 1)))
        assertEquals(100, EventEditing.clampField(DurationField.YEARS, 500))
        assertEquals(0, EventEditing.clampField(DurationField.DAYS, -3))
        assertEquals(5, EventEditing.withDuration(EventOptions(), DurationField.HOURS, 5).timerHours)
    }

    @Test fun `validity of drafts`() {
        val s = ms(2026, 1, 1)
        assertTrue(EventEditing.isValid(ev(EventKind.COUNTDOWN, s, s + 1000), utc))
        assertFalse(EventEditing.isValid(ev(EventKind.COUNTDOWN, s, s), utc))
        assertFalse(EventEditing.isValid(ev(EventKind.COUNTDOWN, s, s + 1000, name = "  "), utc))
        assertTrue(EventEditing.isValid(ev(EventKind.SINCE, s, null), utc))
        assertFalse(EventEditing.isValid(ev(EventKind.SINCE, s, s - 5), utc))
        assertTrue(EventEditing.isValid(ev(EventKind.TIMER, s, null, EventOptions(timerDays = 1)), utc))
        assertFalse(EventEditing.isValid(ev(EventKind.TIMER, s, null, EventOptions(timerDays = 0)), utc))
    }

    @Test fun `timer end is start plus duration`() {
        val s = ms(2026, 1, 31)
        val e = EventEditing.normalize(ev(EventKind.TIMER, s, null, EventOptions(timerDays = 0, timerMonths = 1)), utc)
        assertEquals(ms(2026, 2, 28), e.endAt)
    }

    @Test fun `repeat availability follows duration`() {
        val s = ms(2026, 1, 1)
        val week = ev(EventKind.TIMER, s, null, EventOptions(timerDays = 7))
        val opts = EventEditing.repeatOptions(week, utc).associate { it.repeat to it.enabled }
        assertFalse(opts.getValue(EventRepeat.DAY))
        assertTrue(opts.getValue(EventRepeat.WEEK))
        assertTrue(opts.getValue(EventRepeat.MONTH))
        assertTrue(opts.getValue(EventRepeat.ON_COMPLETION))
        assertTrue(opts.getValue(EventRepeat.NONE))
        // countdown has no "on completion"
        assertFalse(EventEditing.repeatOptions(ev(EventKind.COUNTDOWN, s, s + 1000), utc).any { it.repeat == EventRepeat.ON_COMPLETION })
        // exactly one period still fits
        assertTrue(EventEditing.repeatAvailable(EventRepeat.WEEK, s, ms(2026, 1, 8), utc))
        assertFalse(EventEditing.repeatAvailable(EventRepeat.WEEK, s, ms(2026, 1, 9), utc))
        assertEquals(listOf(EventRepeat.DAY), EventEditing.unavailableRepeats(week, utc))
    }

    @Test fun `fixRepeat resets a repeat that no longer fits`() {
        val s = ms(2026, 1, 1)
        val e = ev(EventKind.TIMER, s, null, EventOptions(timerDays = 3)).copy(repeat = EventRepeat.DAY)
        assertEquals(EventRepeat.NONE, EventEditing.fixRepeat(e, utc).repeat)
        assertEquals(EventRepeat.WEEK, EventEditing.fixRepeat(e.copy(repeat = EventRepeat.WEEK), utc).repeat)
    }

    @Test fun `span and total text`() {
        val a = ms(2026, 11, 11)
        assertEquals("7 days", EventEditing.totalText(a, ms(2026, 11, 18), utc))
        assertEquals("6d 12h", EventEditing.span(a, ms(2026, 11, 17, 12), false, utc))
        assertEquals("1h 0m".replace(" 0m", ""), EventEditing.span(a, a + 3_600_000, false, utc))
        assertEquals("1m 5s", EventEditing.span(a, a + 65_000, true, utc))
    }

    @Test fun `accessory choices by direction`() {
        assertEquals(listOf(Accessory.LEFT, Accessory.IN, Accessory.NONE), EventEditing.accessoryChoices(true, false).map { it.first })
        assertEquals(listOf(Accessory.PASSED, Accessory.AGO, Accessory.NONE), EventEditing.accessoryChoices(false, false).map { it.first })
        assertEquals(listOf(Accessory.LEFT, Accessory.NONE), EventEditing.accessoryChoices(true, true).map { it.first })
        assertEquals(Accessory.LEFT, EventEditing.effectiveAccessory(Accessory.AUTO, true, false))
        assertEquals(Accessory.PASSED, EventEditing.effectiveAccessory(Accessory.LEFT, false, false))
        assertEquals(Accessory.NONE, EventEditing.effectiveAccessory(Accessory.AUTO, true, true))
    }

    @Test fun `hex parsing and formatting`() {
        assertEquals("#3DBE5E", EventEditing.toHex(0xFF3DBE5E.toInt()))
        assertEquals(0xFF3DBE5E.toInt(), EventEditing.parseHex("3dbe5e"))
        assertEquals(0xFF3DBE5E.toInt(), EventEditing.parseHex(" #3DBE5E "))
        assertEquals(0xFF112233.toInt(), EventEditing.parseHex("#FF112233"))
        assertNull(EventEditing.parseHex("#12"))
        assertNull(EventEditing.parseHex("zzzzzz"))
    }

    @Test fun `hsv round trip`() {
        val samples = listOf(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF, 0xFF000000, 0xFF3DBE5E, 0xFF6B4FBF, 0xFFFFD966)
        for (c in samples.map { it.toInt() }) {
            val h = EventEditing.toHsv(c)
            assertEquals(c, EventEditing.fromHsv(h[0], h[1], h[2]))
        }
        val red = EventEditing.toHsv(0xFFFF0000.toInt())
        assertEquals(0f, red[0], 0.01f)
        assertEquals(120f, EventEditing.toHsv(0xFF00FF00.toInt())[0], 0.01f)
        assertEquals(0xFF0000FF.toInt(), EventEditing.fromHsv(240f, 1f, 1f))
    }

    @Test fun `draft codec round trip`() {
        val e = CountEvent(
            id = 5, name = "Trip 🌴", kind = EventKind.TIMER, startAt = 10, endAt = 99, allDay = false, repeat = EventRepeat.ON_COMPLETION,
            countUp = true, notes = "a\nb", archived = true, createdAt = 1, modifiedAt = 2,
            options = EventOptions(timerDays = 2, design = EventDesign.GRID, bg = 0xFF112233.toInt(), fontId = "gloock"),
        )
        assertEquals(e, EventDraftCodec.decode(EventDraftCodec.encode(e)))
        val open = e.copy(endAt = null, kind = EventKind.SINCE)
        assertEquals(open, EventDraftCodec.decode(EventDraftCodec.encode(open)))
        assertNull(EventDraftCodec.decode("not json"))
    }

    @Test fun `date helpers keep time of day`() {
        val s = ms(2026, 1, 1, 15)
        assertEquals(ms(2026, 3, 5, 15), EventEditing.withDate(s, LocalDate.of(2026, 3, 5), utc))
        assertEquals(ms(2026, 1, 1, 0), EventEditing.startOfDay(s, utc))
    }
}

class EventEditingConvertTest {
    private val utc = ZoneId.of("UTC")
    private val now = 1_800_000_000_000L
    private val week = 7L * 24 * 3_600_000

    @Test fun `convert between kinds`() {
        val cd = CountEvent(name = "a", kind = EventKind.COUNTDOWN, startAt = now, endAt = now + week, notes = "n")
        val since = EventEditing.convertKind(cd, EventKind.SINCE, now, utc)
        assertEquals(EventKind.SINCE, since.kind); assertTrue(since.countUp); assertNull(since.endAt); assertEquals(now - week, since.startAt)
        assertEquals("n", since.notes)
        val timer = EventEditing.convertKind(since, EventKind.TIMER, now, utc)
        assertEquals(now, timer.startAt); assertEquals(now + week, timer.endAt); assertFalse(timer.countUp)
        val back = EventEditing.convertKind(timer, EventKind.COUNTDOWN, now, utc)
        assertEquals(now + week, back.endAt)
        assertEquals(cd.copy(), EventEditing.convertKind(cd, EventKind.COUNTDOWN, now, utc))
    }
}
