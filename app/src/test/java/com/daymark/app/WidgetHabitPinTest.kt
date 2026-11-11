package com.daymark.app

import com.daymark.app.data.Settings
import com.daymark.app.domain.Habit
import com.daymark.app.widget.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetHabitPinTest {
    private val a = Habit(1, "A", "x", 0L)
    private val b = Habit(2, "B", "y", 0L)
    private fun sel(id: Long) = Settings(selectedHabitId = id)

    @Test fun pinnedWidgetIgnoresSelection() {
        assertEquals(a, WidgetSnapshot.resolveHabit(listOf(a, b), 1, sel(2)))
        assertEquals(b, WidgetSnapshot.resolveHabit(listOf(a, b), 2, sel(1)))
    }

    @Test fun deletedPinnedHabitFallsBackToFirstNotSelection() {
        assertEquals(a, WidgetSnapshot.resolveHabit(listOf(a, b), 99, sel(2)))
    }

    @Test fun unclaimedWidgetShowsSelection() {
        assertEquals(b, WidgetSnapshot.resolveHabit(listOf(a, b), null, sel(2)))
    }

    @Test fun claimPrefersKindDefaultThenCurrentlyShown() {
        assertEquals(1L, WidgetSnapshot.claimHabitId(listOf(a, b), 1, sel(2)))
        assertEquals(2L, WidgetSnapshot.claimHabitId(listOf(a, b), 0, sel(2)))
        assertEquals(2L, WidgetSnapshot.claimHabitId(listOf(a, b), 77, sel(2)))
        assertNull(WidgetSnapshot.claimHabitId(emptyList(), 0, sel(2)))
    }
}
