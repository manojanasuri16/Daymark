package com.daymark.app

import com.daymark.app.data.BackupCodec
import com.daymark.app.data.HabitEntity
import com.daymark.app.data.InvalidBackupException
import com.daymark.app.data.RelapseEntity
import com.daymark.app.data.Settings
import com.daymark.app.domain.DayStatus
import com.daymark.app.domain.InsightsData
import com.daymark.app.domain.InsightsEngine
import com.daymark.app.domain.Messages
import com.daymark.app.domain.Milestones
import com.daymark.app.domain.RelapseRecord
import com.daymark.app.domain.StreakEngine
import com.daymark.app.domain.StreakStats.Companion.DAY_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class StreakEngineTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun `no relapses - streak equals time since start`() {
        val start = at(2026, 1, 1)
        val now = start + 10 * DAY_MS + 3_600_000
        val s = StreakEngine.compute(start, emptyList(), now, zone)
        assertEquals(10, s.currentDays)
        assertEquals(s.currentMillis, s.longestMillis)
        assertTrue(s.longestIsCurrent)
        assertEquals(0, s.relapseCount)
        assertEquals(11, s.totalCleanDays) // Jan 1..Jan 11 inclusive
    }

    @Test fun `relapse resets current but preserves longest and clean days`() {
        val start = at(2026, 1, 1)
        val relapse = at(2026, 1, 21) // 20-day streak
        val now = at(2026, 1, 26)      // 5 days since
        val s = StreakEngine.compute(start, listOf(relapse), now, zone)
        assertEquals(5, s.currentDays)
        assertEquals(20, s.longestDays)
        assertFalse(s.longestIsCurrent)
        assertEquals(1, s.relapseCount)
        assertEquals(26 - 1, s.totalCleanDays) // 26 tracked days minus 1 relapse day
        assertEquals(2, s.streaks.size)
    }

    @Test fun `achievements are earned on longest streak and survive relapse`() {
        val start = at(2026, 1, 1)
        val s = StreakEngine.compute(start, listOf(at(2026, 2, 5)), at(2026, 2, 5, 20), zone)
        assertEquals(0, s.currentDays)
        val earned = Milestones.earned(s.longestDays).map { it.days }
        assertEquals(listOf(1, 3, 7, 14, 30), earned)
    }

    @Test fun `relapse before journey start extends tracking`() {
        val start = at(2026, 3, 10)
        val early = at(2026, 3, 1)
        val now = at(2026, 3, 12)
        val spans = StreakEngine.spans(start, listOf(early), now)
        assertEquals(early, spans.first().start)
        assertEquals(1, spans.size)
    }

    @Test fun `future relapses are ignored`() {
        val start = at(2026, 1, 1)
        val now = at(2026, 1, 5)
        val s = StreakEngine.compute(start, listOf(at(2026, 2, 1)), now, zone)
        assertEquals(4, s.currentDays)
        assertEquals(0, s.relapseCount)
    }

    @Test fun `multiple relapses same day count once in clean days`() {
        val start = at(2026, 1, 1)
        val now = at(2026, 1, 10)
        val s = StreakEngine.compute(start, listOf(at(2026, 1, 5, 9), at(2026, 1, 5, 18)), now, zone)
        assertEquals(10 - 1, s.totalCleanDays)
        assertEquals(3, s.streaks.size)
    }

    @Test fun `day status colours`() {
        val start = at(2026, 1, 3)
        val rel = setOf(LocalDate.of(2026, 1, 5))
        val today = LocalDate.of(2026, 1, 8)
        fun st(d: Int) = StreakEngine.dayStatus(LocalDate.of(2026, 1, d), start, rel, null, today, zone)
        assertEquals(DayStatus.UNTRACKED, st(2))
        assertEquals(DayStatus.CLEAN, st(3))
        assertEquals(DayStatus.RELAPSE, st(5))
        assertEquals(DayStatus.CLEAN, st(8))
        assertEquals(DayStatus.UNTRACKED, st(9))
    }

    @Test fun `milestone progress`() {
        assertEquals(0.5f, Milestones.progress(2 * DAY_MS), 0.001f) // between 1 and 3 days
        assertEquals(3, Milestones.next(1)?.days)
        assertEquals(1f, Milestones.progress(4000 * DAY_MS), 0f)
    }

    @Test fun `insights aggregate triggers and trend`() {
        val recs = listOf(
            RelapseRecord(1, at(2026, 1, 2, 22), listOf("Stress", "Boredom"), 2, "Alone at home", ""),
            RelapseRecord(2, at(2026, 1, 9, 8), listOf("Stress"), 3, "At work", ""),
        )
        val spans = StreakEngine.spans(at(2026, 1, 1), recs.map { it.timestamp }, at(2026, 1, 20))
        val data = InsightsEngine.compute(recs, spans, at(2026, 1, 20), zone)
        assertEquals("Stress" to 2, data.topTriggers.first())
        assertEquals(2.5, data.averageMood!!, 0.0001)
        assertEquals(1, data.timeOfDay.first { it.first == "Night" }.second)
        assertEquals(1, data.timeOfDay.first { it.first == "Morning" }.second)
        assertEquals(2, data.monthlyRelapses.last().second)
        assertEquals(InsightsData.Trend.IMPROVING, InsightsEngine.trend(listOf(1.0, 2.0, 5.0, 8.0)))
        assertEquals(InsightsData.Trend.NOT_ENOUGH_DATA, InsightsEngine.trend(listOf(1.0)))
    }

    @Test fun `backup round trip with multiple trackers`() {
        val settings = Settings(onboarded = true, accent = "ocean")
        val habits = listOf(
            HabitEntity(id = 7, name = "Sugar", emoji = "🌱", startAt = at(2026, 1, 1)),
            HabitEntity(id = 9, name = "Smoking", emoji = "🚭", startAt = at(2026, 2, 1)),
        )
        val entities = listOf(
            RelapseEntity(id = 5, habitId = 7, timestamp = at(2026, 1, 4), triggers = "Stress|Late night", mood = 2, situation = "Online", notes = "ok"),
            RelapseEntity(id = 6, habitId = 9, timestamp = at(2026, 2, 3), triggers = "Cravings", mood = 1),
        )
        val file = BackupCodec.decode(BackupCodec.encode(settings, habits, entities, now = at(2026, 3, 1)))
        assertEquals(listOf("Sugar", "Smoking"), file.habits.map { it.name })
        assertEquals("ocean", file.accent)
        val (h, r) = BackupCodec.toEntities(file)
        assertEquals(2, h.size)
        val sugarEntries = r.filter { it.habitId == h[0].id }.map { it.toRecord() }
        assertEquals(listOf("Stress", "Late night"), sugarEntries.single().triggers)
        assertEquals("ok", sugarEntries.single().notes)
        assertEquals(1, r.count { it.habitId == h[1].id })
    }

    @Test fun `v1 backup is upgraded to a single tracker`() {
        val v1 = """{"app":"daymark","version":1,"exportedAt":1,"habitName":"Alcohol","journeyStart":${at(2026, 1, 1)},
            "relapses":[{"timestamp":${at(2026, 1, 9)},"triggers":["Stress"],"mood":2}]}"""
        val file = BackupCodec.decode(v1)
        assertEquals(1, file.habits.size)
        assertEquals("Alcohol", file.habits.single().name)
        val (h, r) = BackupCodec.toEntities(file)
        assertEquals(h.single().id, r.single().habitId)
    }

    @Test(expected = InvalidBackupException::class)
    fun `garbage backup rejected`() { BackupCodec.decode("{\"hello\":1}") }

    @Test fun `daily message is stable`() {
        val d = LocalDate.of(2026, 5, 5)
        assertEquals(Messages.forDate(d), Messages.forDate(d))
    }
}
