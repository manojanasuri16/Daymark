package com.daymark.app

import com.daymark.app.domain.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CalendarImportTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun at(d: Int) = LocalDate.of(2026, 1, d).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun allDayUtcMidnightBecomesLocalMidnight() {
        val utc = LocalDate.of(2026, 3, 5).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 3, 5).atStartOfDay(zone).toInstant().toEpochMilli(), CalendarImport.localStart(utc, true, zone))
        assertEquals(utc, CalendarImport.localStart(utc, false, zone))
    }

    @Test fun blankTitlesAreDropped() {
        assertNull(CalendarImport.entry(1, "  ", 5, false, zone))
        assertNull(CalendarImport.entry(1, null, 5, false, zone))
        assertEquals("Dentist", CalendarImport.entry(1, " Dentist ", 5, false, zone)?.title)
    }

    @Test fun availableHidesPastImportedAndDuplicates() {
        val list = listOf(
            CalendarEntry("a", "Trip", at(20), true),
            CalendarEntry("b", "trip", at(20), true),
            CalendarEntry("c", "Old", at(1) - 1000, false),
            CalendarEntry("d", "Party", at(10), true),
            CalendarEntry("e", "Gym", at(5), false),
        )
        val have = listOf(CountEvent(name = "Gym", kind = EventKind.COUNTDOWN, startAt = now, endAt = at(5)))
        val out = CalendarImport.available(list, have, now)
        assertEquals(listOf("Party", "Trip"), out.map { it.title })
    }

    @Test fun sameTitleOtherDateIsKept() {
        val have = listOf(CountEvent(name = "Gym", kind = EventKind.COUNTDOWN, startAt = now, endAt = at(5)))
        val out = CalendarImport.available(listOf(CalendarEntry("x", "Gym", at(6), false)), have, now)
        assertEquals(1, out.size)
    }

    @Test fun searchIsCaseInsensitive() {
        val l = listOf(CalendarEntry("a", "Dentist", at(3), true), CalendarEntry("b", "Party", at(4), true))
        assertEquals(1, CalendarImport.search(l, "DENT").size)
        assertEquals(2, CalendarImport.search(l, " ").size)
    }

    @Test fun toEventIsCountdownToStart() {
        val e = CalendarImport.toEvent(CalendarEntry("a", "Party", at(10), true), now, 3)
        assertEquals(EventKind.COUNTDOWN, e.kind)
        assertEquals(now, e.startAt)
        assertEquals(at(10), e.endAt)
        assertTrue(e.allDay)
        assertTrue(EventEditing.isValid(e))
    }
}
