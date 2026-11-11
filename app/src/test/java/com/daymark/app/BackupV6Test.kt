package com.daymark.app

import com.daymark.app.data.BackupCodec
import com.daymark.app.data.BackupFile
import com.daymark.app.data.HabitEntity
import com.daymark.app.data.NoteEntity
import com.daymark.app.data.Settings
import com.daymark.app.domain.StreakEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZoneId

class BackupV6Test {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun `v6 round trip keeps not-a-slip notes with fresh habit ids`() {
        val habits = listOf(
            HabitEntity(id = 3, name = "Sugar", emoji = "🍬", startAt = at(2026, 1, 1)),
            HabitEntity(id = 8, name = "Vaping", emoji = "🚭", startAt = at(2026, 2, 2)),
        )
        val notes = listOf(
            NoteEntity(id = 1, habitId = 3, timestamp = at(2026, 1, 5), notes = "Had a craving, didn't give in"),
            NoteEntity(id = 2, habitId = 3, timestamp = at(2026, 1, 9), notes = "Ate fruit instead"),
            NoteEntity(id = 3, habitId = 8, timestamp = at(2026, 2, 7), notes = "Long walk helped"),
        )

        val text = BackupCodec.encode(Settings(onboarded = true), habits, emptyList(), now = at(2026, 3, 1), notes = notes)
        val file = BackupCodec.decode(text)
        assertEquals(6, file.version)
        assertEquals(2, file.habits[0].notes.size)
        assertEquals(1, file.habits[1].notes.size)

        val e = BackupCodec.toEntities(file)
        assertEquals(listOf(1L, 2L), e.habits.map { it.id })
        assertEquals(3, e.notes.size)
        assertEquals(2, e.notes.count { it.habitId == 1L })
        assertEquals(1, e.notes.count { it.habitId == 2L })
        assertTrue(e.notes.filter { it.habitId == 1L }.all { it.notes.isNotEmpty() })

        val sugarNotes = e.notes.filter { it.habitId == 1L }
        assertEquals(listOf(at(2026, 1, 5), at(2026, 1, 9)), sugarNotes.map { it.timestamp })
        assertEquals(listOf("Had a craving, didn't give in", "Ate fruit instead"), sugarNotes.map { it.notes })

        val vapingNote = e.notes.single { it.habitId == 2L }
        assertEquals(at(2026, 2, 7), vapingNote.timestamp)
        assertEquals("Long walk helped", vapingNote.notes)
    }

    @Test fun `v5 backup without notes still decodes`() {
        val v5 = """{"app":"daymark","version":5,"exportedAt":1,"habits":[{"name":"Vaping","emoji":"🚭","startAt":${at(2026, 1, 1)},
            "relapses":[{"timestamp":${at(2026, 1, 4)},"triggers":["Stress"],"mood":2,"situation":"","notes":"slipped"},
            {"timestamp":${at(2026, 1, 10)},"triggers":[],"mood":1,"situation":"","notes":""}],
            "spendAmount":0.0,"spendPeriodDays":7,"dailyLimit":0,"reasons":["Health"],"urges":[],"journal":[],
            "kind":0,"goalTarget":0.0,"goalUnit":"","goalStep":1.0,"scheduleDays":127,"reminderMode":0,
            "reminderInterval":60,"reminderStart":480,"reminderEnd":1260,"reminderTimes":[],"completions":[]}]}"""
        val file = BackupCodec.decode(v5)
        assertEquals(5, file.version)
        val e = BackupCodec.toEntities(file)
        assertTrue(e.notes.isEmpty())
        assertEquals(2, e.relapses.size)
        assertEquals("Stress", e.relapses.first().triggers)
        assertEquals("slipped", e.relapses.first().notes)
        assertEquals(1, e.habits.size)
    }

    @Test fun `notes never affect streaks`() {
        val start = at(2026, 1, 1)
        val now = at(2026, 3, 1)
        val zone = ZoneId.of("UTC")
        val habits = listOf(HabitEntity(id = 1, name = "Sugar", emoji = "🍬", startAt = start))
        val notes = listOf(NoteEntity(id = 1, habitId = 1, timestamp = at(2026, 1, 15), notes = "Not a slip, just a note"))

        val withNotes = BackupCodec.toEntities(
            BackupCodec.decode(BackupCodec.encode(Settings(), habits, emptyList(), now = now, notes = notes)),
        )
        val withoutNotes = BackupCodec.toEntities(
            BackupCodec.decode(BackupCodec.encode(Settings(), habits, emptyList(), now = now)),
        )
        assertEquals(1, withNotes.notes.size)
        assertTrue(withoutNotes.notes.isEmpty())

        val a = StreakEngine.compute(withNotes.habits[0].startAt, withNotes.relapses.map { it.timestamp }, now, zone)
        val b = StreakEngine.compute(withoutNotes.habits[0].startAt, withoutNotes.relapses.map { it.timestamp }, now, zone)
        assertEquals(b.currentMillis, a.currentMillis)
        assertEquals(b.totalCleanDays, a.totalCleanDays)
        assertEquals(b.longestMillis, a.longestMillis)
        assertEquals(b.relapseCount, a.relapseCount)
    }

    @Test fun `backup version is 6`() {
        assertEquals(6, BackupFile.VERSION)
    }
}
