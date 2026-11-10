package com.daymark.app

import com.daymark.app.domain.BuildDay
import com.daymark.app.domain.BuildEngine
import com.daymark.app.domain.Completion
import com.daymark.app.domain.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class BuildEngineTest {
    private val zone = ZoneOffset.UTC
    private val every = Schedule.EVERY_DAY
    private val weekdays = Schedule.of(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
    private val monWedFri = Schedule.of(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))

    private fun d(m: Int, day: Int): LocalDate = LocalDate.of(2026, m, day)

    /** Consecutive days from [from] to [to] (inclusive), each logged at [amount]. */
    private fun doneRange(from: LocalDate, to: LocalDate, amount: Double = 1.0): List<Pair<LocalDate, Double>> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { it to amount }.toList()

    private fun stats(start: LocalDate, mask: Int, target: Double, today: LocalDate, log: List<Pair<LocalDate, Double>>) =
        BuildEngine.compute(
            startAt = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            mask = mask,
            target = target,
            completions = log.map { Completion(1L, it.first.toEpochDay(), it.second) },
            today = today,
            zone = zone,
        )

    @Test fun `daily streak counts consecutive done days`() {
        val s = stats(d(3, 1), every, 1.0, d(3, 5), doneRange(d(3, 1), d(3, 5)))
        assertEquals(5, s.currentStreak)
        assertEquals(5, s.longestStreak)
        assertTrue(s.longestIsCurrent)
        assertTrue(s.todayDone)
        assertEquals(0, s.missedDays)
        assertEquals(5, s.dueDays)
        assertEquals(5, s.totalDone)
        assertEquals(1.0, s.completionRate, 1e-9)
    }

    @Test fun `rest days do not break a streak`() {
        // Mon 2 March to Sun 8 March: weekdays only, today is a Sunday (rest day).
        val s = stats(d(3, 2), weekdays, 1.0, d(3, 8), doneRange(d(3, 2), d(3, 6)))
        assertEquals(5, s.currentStreak)
        assertEquals(5, s.longestStreak)
        assertEquals(0, s.missedDays)
        assertEquals(5, s.dueDays)
        assertFalse(s.todayScheduled)
        assertEquals(1.0, s.completionRate, 1e-9)
    }

    @Test fun `a bonus on a rest day counts in total but not in the streak`() {
        val s = stats(d(3, 2), weekdays, 1.0, d(3, 8), doneRange(d(3, 2), d(3, 6)) + listOf(d(3, 7) to 1.0))
        assertEquals(6, s.totalDone)
        assertEquals(5, s.currentStreak)
    }

    @Test fun `pending today neither breaks nor extends the streak`() {
        val s = stats(d(3, 1), every, 1.0, d(3, 5), doneRange(d(3, 1), d(3, 4)))
        assertEquals(4, s.currentStreak)
        assertEquals(4, s.longestStreak)
        assertEquals(0, s.missedDays)
        assertFalse(s.todayDone)
        assertEquals(1.0, s.completionRate, 1e-9)
        assertEquals(BuildDay.PENDING, BuildEngine.status(d(3, 5), every, 1.0, emptyMap(), d(3, 1), d(3, 5)))
    }

    @Test fun `a miss resets the current streak but keeps the longest`() {
        val log = doneRange(d(3, 1), d(3, 5)) + doneRange(d(3, 7), d(3, 9))
        val s = stats(d(3, 1), every, 1.0, d(3, 10), log)
        assertEquals(3, s.currentStreak)
        assertEquals(5, s.longestStreak)
        assertFalse(s.longestIsCurrent)
        assertEquals(1, s.missedDays)
        assertEquals(9, s.dueDays)
        assertEquals(8, s.totalDone)
        assertEquals(8.0 / 9.0, s.completionRate, 1e-9)
        assertEquals(8.0, s.totalAmount, 1e-9)
    }

    @Test fun `partial days are not done`() {
        val amounts = mapOf(d(3, 1) to 2.0, d(3, 2) to 3.0, d(3, 3) to 1.0)
        val s = stats(d(3, 1), every, 3.0, d(3, 3), amounts.toList())
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.missedDays)
        assertFalse(s.todayDone)
        assertEquals(1f / 3f, s.todayProgress, 1e-4f)
        assertEquals(6.0, s.totalAmount, 1e-9)
        assertEquals(2.0, s.averageAmount, 1e-9)
        assertEquals(BuildDay.PARTIAL, BuildEngine.status(d(3, 1), every, 3.0, amounts, d(3, 1), d(3, 3)))
        assertEquals(BuildDay.DONE, BuildEngine.status(d(3, 2), every, 3.0, amounts, d(3, 1), d(3, 3)))
        assertEquals(BuildDay.PENDING, BuildEngine.status(d(3, 3), every, 3.0, amounts, d(3, 1), d(3, 3)))
    }

    @Test fun `goal-less habit is done at one check-off`() {
        val s = stats(d(3, 1), every, 0.0, d(3, 1), listOf(d(3, 1) to 1.0))
        assertEquals(1, s.currentStreak)
        assertTrue(s.todayDone)
        assertEquals(1.0, s.todayTarget, 0.0)
        assertFalse(BuildEngine.isDone(0.5, 0.0))
        assertTrue(BuildEngine.isDone(1.0, 0.0))
        assertEquals(1.0, BuildEngine.effectiveTarget(0.0), 0.0)
    }

    @Test fun `completion rate, missed and due days follow the schedule`() {
        // Mon/Wed/Fri from Mon 2 March to Sun 8 March: Mon and Fri done, Wed missed.
        val s = stats(d(3, 2), monWedFri, 1.0, d(3, 8), listOf(d(3, 2) to 1.0, d(3, 6) to 1.0))
        assertEquals(3, s.dueDays)
        assertEquals(1, s.missedDays)
        assertEquals(2, s.totalDone)
        assertEquals(2.0 / 3.0, s.completionRate, 1e-9)
        assertEquals(2.0 / 3.0, s.last7Rate, 1e-9)
        assertEquals(2.0 / 3.0, s.last30Rate, 1e-9)
        assertEquals(1, s.currentStreak)
        assertEquals(1, s.longestStreak)
        assertFalse(s.todayScheduled)
    }

    @Test fun `weekly list has eight Monday-start weeks ending this week`() {
        val s = stats(d(3, 2), monWedFri, 1.0, d(3, 8), listOf(d(3, 2) to 1.0, d(3, 6) to 1.0))
        assertEquals(8, s.weekly.size)
        assertTrue(s.weekly.all { it.first.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(d(3, 2), s.weekly.last().first)
        assertEquals(2.0 / 3.0, s.weekly.last().second, 1e-9)
        // Eight weeks back from 2 March 2026 is 12 January 2026, before the start, so it reads 0.
        assertEquals(LocalDate.of(2026, 1, 12), s.weekly.first().first)
        assertEquals(0.0, s.weekly.first().second, 0.0)
    }

    @Test fun `start date extends back to an earlier completion`() {
        val s = stats(d(3, 5), every, 1.0, d(3, 6), listOf(d(3, 2) to 1.0))
        assertEquals(d(3, 2), s.startDate)
        assertEquals(1.0, s.totalAmount, 1e-9)
    }

    @Test fun `a zero-amount entry does not move the start date`() {
        val s = stats(d(3, 5), every, 1.0, d(3, 6), listOf(d(2, 20) to 0.0))
        assertEquals(d(3, 5), s.startDate)
    }

    @Test fun `status covers done, partial, missed, pending, rest and untracked`() {
        val amounts = mapOf(d(3, 2) to 2.0, d(3, 3) to 1.0)
        val today = d(3, 6)
        val start = d(3, 1)
        assertEquals(BuildDay.REST, BuildEngine.status(d(3, 1), weekdays, 2.0, amounts, start, today))
        assertEquals(BuildDay.DONE, BuildEngine.status(d(3, 2), weekdays, 2.0, amounts, start, today))
        assertEquals(BuildDay.PARTIAL, BuildEngine.status(d(3, 3), weekdays, 2.0, amounts, start, today))
        assertEquals(BuildDay.MISSED, BuildEngine.status(d(3, 4), weekdays, 2.0, amounts, start, today))
        assertEquals(BuildDay.PENDING, BuildEngine.status(today, weekdays, 2.0, amounts, start, today))
        assertEquals(BuildDay.UNTRACKED, BuildEngine.status(d(3, 7), weekdays, 2.0, amounts, start, today))
        assertEquals(BuildDay.UNTRACKED, BuildEngine.status(d(2, 28), weekdays, 2.0, amounts, start, today))
    }

    @Test fun `status on a rest day with progress`() {
        val today = d(3, 6)
        assertEquals(BuildDay.DONE, BuildEngine.status(d(3, 1), weekdays, 2.0, mapOf(d(3, 1) to 2.0), d(3, 1), today))
        assertEquals(BuildDay.PARTIAL, BuildEngine.status(d(3, 1), weekdays, 2.0, mapOf(d(3, 1) to 1.0), d(3, 1), today))
    }

    @Test fun `schedule labels and normalisation`() {
        assertEquals("Every day", Schedule.label(every))
        assertEquals("Every day", Schedule.label(0))
        assertEquals("Weekdays", Schedule.label(weekdays))
        assertEquals("Weekends", Schedule.label(Schedule.of(listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))))
        assertEquals("Mon, Wed, Fri", Schedule.label(monWedFri))

        assertEquals(127, Schedule.normalize(0))
        assertEquals(127, Schedule.normalize(128))
        assertEquals(21, Schedule.normalize(monWedFri or 128))
        assertEquals(21, monWedFri)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), Schedule.days(monWedFri))
        assertTrue(Schedule.isScheduled(monWedFri, DayOfWeek.FRIDAY))
        assertFalse(Schedule.isScheduled(monWedFri, DayOfWeek.SATURDAY))
    }
}
