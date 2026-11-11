package com.daymark.app

import com.daymark.app.data.BackupCodec
import com.daymark.app.data.BackupFile
import com.daymark.app.data.EventEntity
import com.daymark.app.data.HabitEntity
import com.daymark.app.data.Settings
import com.daymark.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class BackupV7Test {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val habits = listOf(HabitEntity(id = 1, name = "Sugar", emoji = "🍬", startAt = at(2026, 1, 1)))

    @Test fun `version is 7`() = assertEquals(7, BackupFile.VERSION)

    @Test fun `events round trip with non default options`() {
        val a = CountEvent(
            id = 9, name = "🎂 Party", kind = EventKind.COUNTDOWN, startAt = at(2026, 1, 1), endAt = at(2026, 12, 1),
            allDay = false, repeat = EventRepeat.YEAR, countUp = true, notes = "bring cake", archived = false,
            createdAt = 5, modifiedAt = 6,
            options = EventOptions(showPercent = true, multiUnits = 4, design = EventDesign.ROW, bg = 0xFF336699.toInt(), fontId = "marker"),
        )
        val b = CountEvent(
            id = 20, name = "Focus", kind = EventKind.TIMER, startAt = at(2026, 2, 2), endAt = at(2026, 2, 3), repeat = EventRepeat.ON_COMPLETION,
            archived = true, options = EventOptions(timerDays = 0, timerHours = 2, design = EventDesign.MINIMAL, icon = "bolt", showNameOutside = false),
        )
        val text = BackupCodec.encode(Settings(onboarded = true), habits, emptyList(), now = at(2026, 3, 1), events = listOf(a, b).map { EventEntity.from(it) })
        val file = BackupCodec.decode(text)
        assertEquals(7, file.version)
        assertEquals(2, file.events.size)
        val e = BackupCodec.toEntities(file)
        assertEquals(listOf(1L, 2L), e.events.map { it.id })
        assertEquals(a.copy(id = 1), e.events[0].toEvent())
        assertEquals(b.copy(id = 2), e.events[1].toEvent())
    }

    @Test fun `v6 file without events decodes with none`() {
        val v6 = """{"app":"daymark","version":6,"exportedAt":1,"habits":[{"name":"A","startAt":${at(2026, 1, 1)}}]}"""
        val file = BackupCodec.decode(v6)
        assertEquals(6, file.version)
        assertTrue(BackupCodec.toEntities(file).events.isEmpty())
    }

    @Test fun `garbage event options still restore with defaults`() {
        val v7 = """{"app":"daymark","version":7,"exportedAt":1,"habits":[{"name":"A","startAt":${at(2026, 1, 1)}}],
            "events":[{"name":"X","startAt":5,"kind":99,"repeat":99,"options":"???","unknownField":1}]}"""
        val e = BackupCodec.toEntities(BackupCodec.decode(v7))
        assertEquals(1, e.events.size)
        assertEquals(EventOptions(), e.events[0].toEvent().options)
        assertEquals(EventKind.COUNTDOWN, e.events[0].toEvent().kind)
    }
}
