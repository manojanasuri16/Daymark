package com.daymark.app

import com.daymark.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class EventEngineTest {
    private val utc = ZoneId.of("UTC")
    private fun ms(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0, zone: ZoneId = utc) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    private fun fmt(e: CountEvent, now: Long, zone: ZoneId = utc, precision: Boolean = true) =
        EventEngine.format(e, now, zone, precision, Locale.ENGLISH)

    private fun countdown(start: Long, end: Long, o: EventOptions = EventOptions(), repeat: EventRepeat = EventRepeat.NONE) =
        CountEvent(id = 1, name = "X", kind = EventKind.COUNTDOWN, startAt = start, endAt = end, repeat = repeat, countUp = false, options = o)

    private fun since(start: Long, end: Long? = null, o: EventOptions = EventOptions()) =
        CountEvent(id = 2, name = "S", kind = EventKind.SINCE, startAt = start, endAt = end, countUp = true, options = o)

    // region countdown / since / timer text

    @Test fun `countdown rounds up by default`() {
        val e = countdown(ms(2026, 11, 11), ms(2026, 11, 18))
        val t = fmt(e, ms(2026, 11, 11, 11, 44))
        assertEquals("7 days left", t.primary)
        assertEquals("18 Nov 2026", t.detailDate)
    }

    @Test fun `round down shows fewer days`() {
        val e = countdown(ms(2026, 11, 11), ms(2026, 11, 18), EventOptions(roundUp = false))
        assertEquals("6 days left", fmt(e, ms(2026, 11, 11, 11, 44)).primary)
    }

    @Test fun `precision uses a smaller unit below two`() {
        val e = countdown(ms(2026, 11, 1), ms(2026, 11, 18))
        assertEquals("36 hours left", fmt(e, ms(2026, 11, 16, 12)).primary)
        assertEquals("2 days left", fmt(e, ms(2026, 11, 16, 12), precision = false).primary)
        val off = e.copy(options = EventOptions(precision = false))
        assertEquals("2 days left", fmt(off, ms(2026, 11, 16, 12)).primary)
        // 90 minutes left: hours would be 1.5, so minutes
        assertEquals("90 minutes left", fmt(e, ms(2026, 11, 17, 22, 30)).primary)
    }

    @Test fun `one unit is singular`() {
        val e = countdown(ms(2026, 11, 1), ms(2026, 11, 18), EventOptions(singleUnit = CountUnit.DAYS, precision = false))
        assertEquals("1 day left", fmt(e, ms(2026, 11, 17)).primary)
    }

    @Test fun `since defaults to rounding down and counts passed`() {
        val e = since(ms(2026, 11, 11))
        assertEquals("11 hours passed", fmt(e, ms(2026, 11, 11, 11, 44)).primary)
        val days = since(ms(2026, 11, 1), o = EventOptions(precision = false))
        assertEquals("10 days passed", fmt(days, ms(2026, 11, 11, 23)).primary)
        assertEquals("Today", fmt(since(ms(2026, 11, 11)), ms(2026, 11, 11, 11, 44)).detailDate)
    }

    @Test fun `since rounds up when asked`() {
        val e = since(ms(2026, 11, 1), o = EventOptions(roundUp = true, precision = false))
        assertEquals("11 days passed", fmt(e, ms(2026, 11, 11, 23)).primary)
    }

    @Test fun `multiple units breakdown`() {
        val e = since(ms(2026, 11, 11), o = EventOptions(multiUnits = 5))
        assertEquals("11h 44m passed", fmt(e, ms(2026, 11, 11, 11, 44, 20)).primary)
        val r = since(ms(2026, 11, 11), o = EventOptions(multiUnits = 5, roundUp = true))
        assertEquals("11h 45m passed", fmt(r, ms(2026, 11, 11, 11, 44, 20)).primary)
        val three = since(ms(2026, 1, 1), o = EventOptions(multiUnits = 3))
        assertEquals("10mo 10d passed", fmt(three, ms(2026, 11, 11, 11, 45)).primary)
        val two = since(ms(2024, 1, 1), o = EventOptions(multiUnits = 2))
        assertEquals("2y 10mo passed", fmt(two, ms(2026, 11, 20)).primary)
    }

    @Test fun `multi unit round up carries correctly`() {
        // 59 minutes 30 seconds, 5 units, round up -> 1h 0m
        val e = since(ms(2026, 11, 11), o = EventOptions(multiUnits = 5, roundUp = true))
        assertEquals("1h 0m passed", fmt(e, ms(2026, 11, 11, 0, 59, 30)).primary)
        // 3 units round up Jan 31 -> Mar 1 00:10: 1 month 1 day and a bit -> 1mo 2d
        val b = EventEngine.breakdownRounded(ms(2026, 1, 31), ms(2026, 3, 1, 0, 10), 3, true, utc)
        assertEquals(listOf(0L, 1L, 2L, 0L, 0L), b.asList())
    }

    @Test fun `breakdown is calendar correct for month ends and leap days`() {
        assertEquals(listOf(0L, 1L, 1L, 0L, 0L), EventEngine.breakdown(ms(2026, 1, 31), ms(2026, 3, 1), utc).asList())
        assertEquals(listOf(0L, 11L, 30L, 0L, 0L), EventEngine.breakdown(ms(2028, 2, 29), ms(2029, 2, 28), utc).asList())
        assertEquals(listOf(1L, 0L, 1L, 0L, 0L), EventEngine.breakdown(ms(2028, 2, 29), ms(2029, 3, 1), utc).asList())
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L), EventEngine.breakdown(5, 5, utc).asList())
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), EventEngine.breakdown(ms(2020, 1, 1), ms(2021, 3, 4, 4, 5), utc).asList())
    }

    @Test fun `single month and year units are calendar based`() {
        val m = countdown(ms(2026, 11, 11), ms(2027, 2, 11), EventOptions(singleUnit = CountUnit.MONTHS))
        assertEquals("3 months left", fmt(m, ms(2026, 11, 11)).primary)
        val y = countdown(ms(2026, 1, 1), ms(2030, 1, 1), EventOptions(singleUnit = CountUnit.YEARS, roundUp = false))
        assertEquals("3 years left", fmt(y, ms(2026, 6, 1)).primary)
        val w = countdown(ms(2026, 11, 1), ms(2026, 12, 1), EventOptions(singleUnit = CountUnit.WEEKS))
        assertEquals("3 weeks left", fmt(w, ms(2026, 11, 10)).primary)
        assertEquals("4 weeks left", fmt(w, ms(2026, 11, 8)).primary)
    }

    @Test fun `days stay whole across a daylight saving change`() {
        val ny = ZoneId.of("America/New_York")
        val e = countdown(ms(2026, 10, 29, zone = ny), ms(2026, 11, 5, zone = ny), EventOptions(roundUp = false))
        assertEquals("7 days left", fmt(e, ms(2026, 10, 29, zone = ny), ny).primary)
        assertEquals("6 days left", fmt(e, ms(2026, 10, 29, 12, zone = ny), ny).primary)
    }

    @Test fun `accessories`() {
        val base = countdown(ms(2026, 11, 11), ms(2026, 11, 18))
        val now = ms(2026, 11, 11)
        assertEquals("In 7 days", fmt(base.copy(options = EventOptions(accessory = Accessory.IN)), now).primary)
        assertEquals("7 days", fmt(base.copy(options = EventOptions(accessory = Accessory.NONE)), now).primary)
        assertEquals("7 days ago", fmt(base.copy(options = EventOptions(accessory = Accessory.AGO)), now).primary)
        assertEquals("7 days passed", fmt(base.copy(options = EventOptions(accessory = Accessory.PASSED)), now).primary)
        assertEquals("7 days left", fmt(base.copy(options = EventOptions(accessory = Accessory.LEFT)), now).primary)
        assertEquals("3 days left", EventEngine.accessoryText(Accessory.AUTO, "3 days", true))
        assertEquals("3 days passed", EventEngine.accessoryText(Accessory.AUTO, "3 days", false))
    }

    @Test fun `countup toggle on a countdown shows elapsed time`() {
        val e = countdown(ms(2026, 11, 1), ms(2026, 11, 18), EventOptions(precision = false, roundUp = false)).copy(countUp = true)
        assertEquals("10 days passed", fmt(e, ms(2026, 11, 11)).primary)
    }

    @Test fun `percent mode`() {
        val today = countdown(ms(2026, 11, 11), ms(2026, 11, 12), EventOptions(showPercent = true, accessory = Accessory.NONE))
        assertEquals("50%", fmt(today, ms(2026, 11, 11, 12)).primary)
        val left = today.copy(options = EventOptions(showPercent = true, accessory = Accessory.LEFT))
        assertEquals("50% left", fmt(left, ms(2026, 11, 11, 12)).primary)
        assertEquals("0%", fmt(today, ms(2026, 11, 11)).primary)
        // never 100% before it finishes
        assertEquals("99%", fmt(today, ms(2026, 11, 11, 23, 59, 59)).primary)
        // open-ended since falls back to time text
        assertEquals("3 days passed", fmt(since(ms(2026, 11, 1), o = EventOptions(showPercent = true, precision = false)), ms(2026, 11, 4)).primary)
    }

    @Test fun `completed countdown`() {
        val e = countdown(ms(2026, 11, 1), ms(2026, 11, 5))
        val p = EventEngine.progress(e, ms(2026, 11, 6), utc)
        assertTrue(p.completed)
        assertEquals(1f, p.fraction!!, 0f)
        assertEquals(0L, p.remainingMs)
        assertEquals("Completed", fmt(e, ms(2026, 11, 6)).primary)
        assertEquals("100%", fmt(e.copy(options = EventOptions(showPercent = true)), ms(2026, 11, 6)).primary)
    }

    @Test fun `not started yet countdown has zero progress`() {
        val e = countdown(ms(2026, 12, 1), ms(2026, 12, 11))
        val p = EventEngine.progress(e, ms(2026, 11, 20), utc)
        assertEquals(0L, p.elapsedMs)
        assertEquals(0f, p.fraction!!, 0f)
        assertFalse(p.completed)
    }

    @Test fun `since with an end has progress and open since has none`() {
        val p = EventEngine.progress(since(ms(2026, 11, 1), ms(2026, 11, 11)), ms(2026, 11, 6), utc)
        assertEquals(0.5f, p.fraction!!, 0.001f)
        val open = EventEngine.progress(since(ms(2026, 11, 1)), ms(2026, 11, 6), utc)
        assertNull(open.fraction)
        assertNull(open.end)
        assertFalse(open.completed)
    }

    @Test fun `timer counts down and up`() {
        val start = ms(2026, 11, 11, 11, 48)
        val t = CountEvent(
            id = 3, name = "Timer", kind = EventKind.TIMER, startAt = start, endAt = null, countUp = false,
            options = EventOptions(timerDays = 7),
        )
        assertEquals("7 days left", fmt(t, start + 8_000).primary)
        assertEquals("6 days left", fmt(t.copy(options = t.options.copy(roundUp = false)), start + 8_000).primary)
        val c = EventEngine.cycle(t, start + 8_000, utc)
        assertEquals(ms(2026, 11, 18, 11, 48), c.end)
        val up = t.copy(countUp = true, options = t.options.copy(multiUnits = 5))
        assertEquals("0m passed", fmt(up, start + 8_000).primary)
        assertEquals("1d 1h 0m passed", fmt(up, start + 25 * 3_600_000L).primary)
        // calendar-correct duration: Jan 31 + 1 month = Feb 28
        val month = t.copy(startAt = ms(2026, 1, 31), options = EventOptions(timerDays = 0, timerMonths = 1))
        assertEquals(ms(2026, 2, 28), EventEngine.cycle(month, ms(2026, 2, 1), utc).end)
        // completes
        assertTrue(EventEngine.progress(t, start + 8 * 86_400_000L, utc).completed)
    }

    // endregion

    // region repeats

    @Test fun `daily repeat advances the cycle`() {
        val e = countdown(ms(2026, 11, 11), ms(2026, 11, 12), repeat = EventRepeat.DAY)
        val c = EventEngine.cycle(e, ms(2026, 11, 13, 10), utc)
        assertEquals(ms(2026, 11, 13), c.start)
        assertEquals(ms(2026, 11, 14), c.end)
        assertEquals(2, c.index)
        val p = EventEngine.progress(e, ms(2026, 11, 13, 6), utc)
        assertEquals(0.25f, p.fraction!!, 0.001f)
        assertFalse(p.completed)
    }

    @Test fun `weekly and two weekly repeats`() {
        val w = countdown(ms(2026, 11, 2), ms(2026, 11, 9), repeat = EventRepeat.WEEK)
        assertEquals(ms(2026, 11, 16), EventEngine.cycle(w, ms(2026, 11, 17), utc).start)
        val tw = countdown(ms(2026, 11, 2), ms(2026, 11, 16), repeat = EventRepeat.TWO_WEEKS)
        val c = EventEngine.cycle(tw, ms(2026, 12, 1), utc)
        assertEquals(ms(2026, 11, 30), c.start)
        assertEquals(ms(2026, 12, 14), c.end)
    }

    @Test fun `monthly repeat clamps month ends from the original date`() {
        val e = countdown(ms(2026, 1, 31), ms(2026, 2, 1), repeat = EventRepeat.MONTH)
        // Feb cycle is Feb 28 - Mar 1, ended by Mar 1 05:00 -> March cycle starts Mar 31
        val c = EventEngine.cycle(e, ms(2026, 3, 1, 5), utc)
        assertEquals(ms(2026, 3, 31), c.start)
        assertEquals(ms(2026, 4, 1), c.end)
        assertEquals(2, c.index)
        // inside the February cycle
        val feb = EventEngine.cycle(e, ms(2026, 2, 28, 12), utc)
        assertEquals(ms(2026, 2, 28), feb.start)
        assertEquals(1, feb.index)
        // before it started: still the first cycle, nothing elapsed
        val before = EventEngine.cycle(e, ms(2026, 1, 15), utc)
        assertEquals(0, before.index)
    }

    @Test fun `yearly repeat handles February 29`() {
        val e = countdown(ms(2028, 2, 29), ms(2028, 3, 1), repeat = EventRepeat.YEAR)
        val c = EventEngine.cycle(e, ms(2029, 3, 10), utc)
        assertEquals(ms(2030, 2, 28), c.start)
        assertEquals(ms(2030, 3, 1), c.end)
        val leap = EventEngine.cycle(e, ms(2032, 2, 29, 6), utc)
        assertEquals(ms(2032, 2, 29), leap.start)
    }

    @Test fun `period event year runs january to january`() {
        val y = countdown(ms(2026, 1, 1), ms(2027, 1, 1), repeat = EventRepeat.YEAR)
        val c = EventEngine.cycle(y, ms(2028, 6, 1), utc)
        assertEquals(ms(2028, 1, 1), c.start)
        assertEquals(ms(2029, 1, 1), c.end)
    }

    @Test fun `repeating events never complete`() {
        val e = countdown(ms(2026, 11, 1), ms(2026, 11, 2), repeat = EventRepeat.DAY)
        assertFalse(EventEngine.progress(e, ms(2027, 6, 6, 3), utc).completed)
    }

    @Test fun `on completion restarts the timer`() {
        val t = CountEvent(
            id = 4, name = "T", kind = EventKind.TIMER, startAt = ms(2026, 11, 1), endAt = null,
            repeat = EventRepeat.ON_COMPLETION, options = EventOptions(timerDays = 1),
        )
        val c = EventEngine.cycle(t, ms(2026, 11, 3, 6), utc)
        assertEquals(ms(2026, 11, 3), c.start)
        assertEquals(ms(2026, 11, 4), c.end)
        assertEquals(2, c.index)
        assertFalse(EventEngine.progress(t, ms(2027, 1, 1), utc).completed)
        // month timers restart from the previous end
        val m = t.copy(options = EventOptions(timerDays = 0, timerMonths = 1), startAt = ms(2026, 1, 31))
        val c2 = EventEngine.cycle(m, ms(2026, 3, 1), utc)
        assertEquals(ms(2026, 2, 28), c2.start)
        assertEquals(ms(2026, 3, 28), c2.end)
    }

    @Test fun `on completion on a non timer is ignored`() {
        val e = countdown(ms(2026, 11, 1), ms(2026, 11, 2), repeat = EventRepeat.ON_COMPLETION)
        assertTrue(EventEngine.progress(e, ms(2026, 11, 5), utc).completed)
    }

    // endregion

    // region ordering and notifications

    @Test fun `next event ordering skips archived completed and open ended`() {
        val now = ms(2026, 11, 11)
        fun ev(id: Long, endDays: Int, archived: Boolean = false) =
            countdown(ms(2026, 11, 1), now + endDays * 86_400_000L).copy(id = id, archived = archived)
        val list = listOf(ev(1, 3), ev(2, 1), ev(3, 10), ev(4, 0).copy(endAt = ms(2026, 11, 5)), ev(5, 1, archived = true), since(ms(2026, 1, 1)).copy(id = 6))
        assertEquals(2L, EventEngine.nextEvent(list, now, 0, utc)?.id)
        assertEquals(1L, EventEngine.nextEvent(list, now, 1, utc)?.id)
        assertEquals(3L, EventEngine.nextEvent(list, now, 2, utc)?.id)
        assertNull(EventEngine.nextEvent(list, now, 3, utc))
    }

    @Test fun `end based notification times`() {
        val e = countdown(ms(2026, 11, 1), ms(2026, 11, 18))
        val times = EventEngine.notificationTimes(e, ms(2026, 11, 11, 12), utc)
        assertEquals(
            listOf(ms(2026, 11, 11) to NotifType.ONE_WEEK_BEFORE).filter { it.first > ms(2026, 11, 11, 12) } +
                listOf(ms(2026, 11, 17) to NotifType.ONE_DAY_BEFORE, ms(2026, 11, 18) to NotifType.COMPLETED),
            times,
        )
        assertTrue(EventEngine.notificationTimes(e, ms(2026, 12, 1), utc).isEmpty())
        assertTrue(EventEngine.notificationTimes(e.copy(archived = true), ms(2026, 11, 11), utc).isEmpty())
        val early = EventEngine.notificationTimes(e, ms(2026, 11, 2), utc)
        assertEquals(listOf(NotifType.ONE_WEEK_BEFORE, NotifType.ONE_DAY_BEFORE, NotifType.COMPLETED), early.map { it.second })
    }

    @Test fun `after start notification times for count up events`() {
        val s = since(ms(2026, 10, 1))
        val times = EventEngine.notificationTimes(s, ms(2026, 11, 11, 12), utc).associate { it.second to it.first }
        assertEquals(ms(2026, 11, 12), times[NotifType.DAILY_AFTER])
        assertEquals(ms(2026, 11, 12), times[NotifType.WEEKLY_AFTER])
        assertEquals(ms(2026, 12, 1), times[NotifType.MONTHLY_AFTER])
        assertEquals(ms(2027, 10, 1), times[NotifType.YEARLY_AFTER])
        assertEquals(4, times.size)
        // not for plain countdowns
        assertTrue(EventEngine.notificationTimes(countdown(ms(2026, 11, 1), ms(2026, 11, 18)), ms(2026, 11, 11), utc).none { it.second == NotifType.DAILY_AFTER })
    }

    @Test fun `repeating events plan into the next cycle`() {
        val today = countdown(ms(2026, 11, 11), ms(2026, 11, 12), repeat = EventRepeat.DAY)
        val times = EventEngine.notificationTimes(today, ms(2026, 11, 11, 12), utc)
        assertEquals(ms(2026, 11, 12) to NotifType.COMPLETED, times.first { it.second == NotifType.COMPLETED })
        assertTrue(times.any { it.first == ms(2026, 11, 13) && it.second == NotifType.COMPLETED })
    }

    @Test fun `period events are seeded for the current day week month and year`() {
        val now = ms(2026, 11, 11, 11, 44)
        val list = EventDefaults.periodEvents(now, utc, DayOfWeek.MONDAY)
        assertEquals(listOf("Today", "This week", "This month", "This year"), list.map { it.name })
        assertEquals(ms(2026, 11, 9), list[1].startAt)
        assertEquals(ms(2026, 11, 1), list[2].startAt)
        assertEquals(ms(2027, 1, 1), list[3].endAt)
        list.forEach { e ->
            val p = EventEngine.progress(e, now, utc)
            assertFalse(p.completed)
            assertTrue(now in p.start until p.end!!)
        }
        assertEquals("Sunday week start", ms(2026, 11, 8), EventDefaults.periodEvents(now, utc, DayOfWeek.SUNDAY)[1].startAt)
    }

    // endregion
}
