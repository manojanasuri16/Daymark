package com.daymark.app

import com.daymark.app.widget.PendingEventPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingEventPinTest {
    private val pin = PendingEventPin(eventId = 7L, createdAt = 1_000_000L)

    @Test fun liveWithinTwoMinutes() {
        assertTrue(pin.isLive(1_000_000L))
        assertTrue(pin.isLive(1_000_000L + PendingEventPin.TTL_MS))
        assertFalse(pin.isLive(1_000_000L + PendingEventPin.TTL_MS + 1))
    }

    @Test fun futureRequestIsNotLive() = assertFalse(pin.isLive(999_999L))

    @Test fun claimableOnlyWhenFreshAndUnconfigured() {
        val now = 1_030_000L
        assertEquals(pin, PendingEventPin.claimable(pin, now, alreadyConfigured = false))
        assertNull(PendingEventPin.claimable(pin, now, alreadyConfigured = true))
        assertNull(PendingEventPin.claimable(pin, 1_000_000L + 200_000L, alreadyConfigured = false))
        assertNull(PendingEventPin.claimable(null, now, alreadyConfigured = false))
    }

    @Test fun nullEventMeansQueueMode() {
        assertTrue(PendingEventPin(null, 1, 5L).queue)
        assertFalse(pin.queue)
    }
}
