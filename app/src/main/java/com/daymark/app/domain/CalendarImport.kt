package com.daymark.app.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** One upcoming entry read from the device calendar. [startAt] is a real instant in local time (all-day entries already converted). */
data class CalendarEntry(val key: String, val title: String, val startAt: Long, val allDay: Boolean)

/** Pure mapping, de-duplication and search for the one-time calendar import. The ContentResolver query lives in the UI layer. */
object CalendarImport {
    /**
     * CalendarContract reports all-day instances at UTC midnight; this returns the same calendar date at local midnight so the
     * countdown ends on that day wherever the phone is.
     */
    fun localStart(beginMs: Long, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): Long =
        if (allDay) Instant.ofEpochMilli(beginMs).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        else beginMs

    fun entry(id: Long, title: String?, beginMs: Long, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): CalendarEntry? {
        val t = title?.trim().orEmpty()
        if (t.isEmpty()) return null
        val start = localStart(beginMs, allDay, zone)
        return CalendarEntry("$id@$beginMs", t.take(EventEditing.NAME_MAX), start, allDay)
    }

    /** Future entries only, earliest first, each (title, start) once, and without ones already imported as events. */
    fun available(entries: List<CalendarEntry>, existing: List<CountEvent>, now: Long): List<CalendarEntry> {
        val taken = existing.map { it.name.trim().lowercase() to it.endAt }.toSet()
        return entries.asSequence()
            .filter { it.startAt > now }
            .filter { (it.title.lowercase() to it.startAt) !in taken }
            .distinctBy { it.title.lowercase() to it.startAt }
            .sortedBy { it.startAt }
            .toList()
    }

    fun search(entries: List<CalendarEntry>, query: String): List<CalendarEntry> {
        val q = query.trim()
        return if (q.isEmpty()) entries else entries.filter { it.title.contains(q, ignoreCase = true) }
    }

    /** A countdown from [now] to the calendar entry, in a pleasant preset. */
    fun toEvent(entry: CalendarEntry, now: Long, presetIndex: Int): CountEvent = CountEvent(
        name = entry.title,
        kind = EventKind.COUNTDOWN,
        startAt = now,
        endAt = entry.startAt,
        allDay = entry.allDay,
        countUp = false,
        createdAt = now,
        modifiedAt = now,
        options = EventDefaults.optionsFor(EventKind.COUNTDOWN, presetIndex = presetIndex),
    )
}
