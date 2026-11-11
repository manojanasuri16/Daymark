package com.daymark.app

import com.daymark.app.data.BackupCodec
import com.daymark.app.data.BackupCompletion
import com.daymark.app.data.BackupFile
import com.daymark.app.data.BackupHabit
import com.daymark.app.data.CompletionEntity
import com.daymark.app.data.HabitEntity
import com.daymark.app.data.InvalidBackupException
import com.daymark.app.data.RelapseEntity
import com.daymark.app.data.Settings
import com.daymark.app.domain.HabitKind
import com.daymark.app.domain.ReminderMode
import com.daymark.app.domain.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class BackupV5Test {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun day(m: Int, d: Int): Long = LocalDate.of(2026, m, d).toEpochDay()

    @Test fun `v5 round trip keeps break and build trackers`() {
        val monWedFri = Schedule.of(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val habits = listOf(
            HabitEntity(id = 3, name = "Sugar", emoji = "🍬", startAt = at(2026, 1, 1), dailyLimit = 2, reasons = "Energy\nSleep"),
            HabitEntity(
                id = 8, name = "Water", emoji = "💧", startAt = at(2026, 2, 2),
                kind = HabitKind.BUILD.id, goalTarget = 3.0, goalUnit = "L", goalStep = 0.25, scheduleDays = monWedFri,
                reminderMode = ReminderMode.INTERVAL.id, reminderInterval = 90, reminderStart = 480, reminderEnd = 1260,
            ),
        )
        val relapses = listOf(
            RelapseEntity(id = 1, habitId = 3, timestamp = at(2026, 1, 5), triggers = "Stress", mood = 2),
            RelapseEntity(id = 2, habitId = 3, timestamp = at(2026, 1, 20), triggers = "", mood = 1, notes = "late"),
        )
        val completions = listOf(
            CompletionEntity(habitId = 8, epochDay = day(2, 2), amount = 1.5),
            CompletionEntity(habitId = 8, epochDay = day(2, 4), amount = 3.0),
            CompletionEntity(habitId = 8, epochDay = day(2, 6), amount = 0.75),
        )

        val text = BackupCodec.encode(Settings(onboarded = true), habits, relapses, completions = completions, now = at(2026, 3, 1))
        val file = BackupCodec.decode(text)
        assertEquals(BackupFile.VERSION, file.version)
        assertEquals(3, file.habits[1].completions.size)

        val e = BackupCodec.toEntities(file)
        assertEquals(listOf("Sugar", "Water"), e.habits.map { it.name })
        assertEquals(listOf(1L, 2L), e.habits.map { it.id })

        val sugar = e.habits[0].toHabit()
        assertEquals(HabitKind.BREAK, sugar.kind)
        assertEquals(2, sugar.dailyLimit)
        assertEquals(listOf("Energy", "Sleep"), sugar.reasons)
        assertEquals(2, e.relapses.count { it.habitId == e.habits[0].id })
        assertEquals("Stress", e.relapses.first().triggers)

        val water = e.habits[1].toHabit()
        assertEquals(HabitKind.BUILD, water.kind)
        assertEquals(3.0, water.goalTarget, 1e-9)
        assertEquals("L", water.goalUnit)
        assertEquals(0.25, water.goalStep, 1e-9)
        assertEquals(monWedFri, water.scheduleDays)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), Schedule.days(water.scheduleDays).toSet())
        assertEquals(ReminderMode.INTERVAL, water.reminder.mode)
        assertEquals(90, water.reminder.intervalMinutes)
        assertEquals(480, water.reminder.activeStart)
        assertEquals(1260, water.reminder.activeEnd)

        assertTrue(e.completions.none { it.habitId == e.habits[0].id })
        val restored = e.completions.associate { it.epochDay to it.amount }
        assertEquals(3, e.completions.size)
        assertTrue(e.completions.all { it.habitId == e.habits[1].id })
        assertEquals(mapOf(day(2, 2) to 1.5, day(2, 4) to 3.0, day(2, 6) to 0.75), restored)
    }

    @Test fun `reminder times survive the round trip sorted`() {
        val habits = listOf(HabitEntity(id = 1, name = "Read", emoji = "📚", startAt = at(2026, 3, 1), kind = HabitKind.BUILD.id, reminderMode = ReminderMode.TIMES.id, reminderTimes = "1140,540"))
        val e = BackupCodec.toEntities(BackupCodec.decode(BackupCodec.encode(Settings(), habits, emptyList(), now = at(2026, 3, 2))))
        val h = e.habits.single().toHabit()
        assertEquals(ReminderMode.TIMES, h.reminder.mode)
        assertEquals(listOf(540, 1140), h.reminder.times)
    }

    @Test fun `v4 backup decodes as break-a-habit trackers`() {
        val v4 = """{"app":"daymark","version":4,"exportedAt":1,"habits":[{"name":"Vaping","emoji":"🚭","startAt":${at(2026, 1, 1)},
            "relapses":[{"timestamp":${at(2026, 1, 4)},"triggers":["Stress"],"mood":2,"situation":"","notes":""}],
            "spendAmount":5.0,"spendPeriodDays":7,"dailyLimit":0,"reasons":["Health"],"urges":[],
            "journal":[{"timestamp":${at(2026, 1, 2)},"mood":4,"text":"ok","photos":[]}]}]}"""
        val file = BackupCodec.decode(v4)
        assertEquals(4, file.version)
        val e = BackupCodec.toEntities(file)
        val h = e.habits.single().toHabit()
        assertEquals(HabitKind.BREAK, h.kind)
        assertEquals(Schedule.EVERY_DAY, h.scheduleDays)
        assertEquals(ReminderMode.NONE, h.reminder.mode)
        assertEquals(0.0, h.goalTarget, 0.0)
        assertEquals(5.0, h.spendAmount, 1e-9)
        assertEquals(1, e.relapses.size)
        assertEquals(1, e.journal.size)
        assertTrue(e.completions.isEmpty())
    }

    @Test fun `completions are sanitised and merged per day`() {
        val d = day(3, 2)
        val file = BackupFile(
            exportedAt = 1,
            habits = listOf(
                BackupHabit(
                    name = "Water", startAt = at(2026, 3, 1), kind = HabitKind.BUILD.id, goalTarget = Double.NaN,
                    completions = listOf(
                        BackupCompletion(d, 1.0),
                        BackupCompletion(d, 0.5),
                        BackupCompletion(d + 1, 0.0),
                        BackupCompletion(d + 2, Double.NaN),
                        BackupCompletion(d + 3, -2.0),
                    ),
                ),
            ),
        )
        val e = BackupCodec.toEntities(file)
        assertEquals(listOf(d to 1.5), e.completions.map { it.epochDay to it.amount })
        assertEquals(0.0, e.habits.single().toHabit().goalTarget, 0.0)
    }

    @Test(expected = InvalidBackupException::class)
    fun `backup newer than v5 is rejected`() {
        BackupCodec.decode("""{"app":"daymark","version":7,"exportedAt":1,"habits":[{"name":"A","startAt":${at(2026, 1, 1)}}]}""")
    }
}
