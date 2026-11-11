package com.daymark.app

import com.daymark.app.widget.WidgetKind
import com.daymark.app.widget.WidgetOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetOfferTest {
    @Test fun breakTrackersGetAllSixInOrder() {
        assertEquals(
            listOf(WidgetKind.STREAK, WidgetKind.RING, WidgetKind.HEATMAP, WidgetKind.STATS, WidgetKind.QUOTE, WidgetKind.ACTIONS),
            WidgetOffer.kindsFor(isBuild = false),
        )
    }

    @Test fun buildHabitsOnlyGetStreakRingHeatmap() {
        assertEquals(listOf(WidgetKind.STREAK, WidgetKind.RING, WidgetKind.HEATMAP), WidgetOffer.kindsFor(isBuild = true))
        assertFalse(WidgetKind.EVENT in WidgetOffer.kindsFor(false))
    }

    @Test fun tipAppearsOnThirdOpenAndNeverAfterDone() {
        assertFalse(WidgetOffer.shouldShowTip(0))
        assertFalse(WidgetOffer.shouldShowTip(1))
        assertTrue(WidgetOffer.shouldShowTip(2))
        assertFalse(WidgetOffer.shouldShowTip(WidgetOffer.TIP_DONE))
        assertEquals(1, WidgetOffer.nextTipViews(0))
        assertEquals(2, WidgetOffer.nextTipViews(2))
        assertEquals(WidgetOffer.TIP_DONE, WidgetOffer.nextTipViews(WidgetOffer.TIP_DONE))
    }

    @Test fun buttonLabelNamesTheKind() = assertEquals("Add Streak widget", WidgetOffer.buttonLabel(WidgetKind.STREAK))

    @Test fun hintShowsInUpToFiveSessionsThenStops() {
        for (n in 0..4) assertTrue(WidgetOffer.shouldShowHint(n))
        assertFalse(WidgetOffer.shouldShowHint(5))
        assertFalse(WidgetOffer.shouldShowHint(WidgetOffer.HINT_DONE))
        assertEquals(5, WidgetOffer.nextHintShows(4))
        assertEquals(5, WidgetOffer.nextHintShows(5))
        assertEquals(WidgetOffer.HINT_DONE, WidgetOffer.nextHintShows(WidgetOffer.HINT_DONE))
    }

    @Test fun hintCountsOncePerSession() {
        val s = com.daymark.app.widget.HintSession()
        assertEquals(1, s.start(0))
        assertEquals(null, s.start(1)) // tab return / recomposition: no second count
        val used = com.daymark.app.widget.HintSession()
        assertEquals(null, used.start(5))
        val learned = com.daymark.app.widget.HintSession()
        assertEquals(null, learned.start(WidgetOffer.HINT_DONE))
        assertEquals(5, com.daymark.app.widget.HintSession().start(4))
    }

    @Test fun finishingStopsTheSession() {
        val s = com.daymark.app.widget.HintSession()
        s.start(0)
        assertFalse(s.isFinished)
        s.finish()
        assertTrue(s.isFinished)
    }
}
