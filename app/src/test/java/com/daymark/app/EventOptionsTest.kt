package com.daymark.app

import com.daymark.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventOptionsTest {
    @Test fun `round trip keeps every changed field`() {
        val o = EventOptions(
            showPercent = true, multiUnits = 3, singleUnit = CountUnit.WEEKS, roundUp = false, accessory = Accessory.AGO,
            precision = false, timerDays = 0, timerHours = 5, design = EventDesign.GRID, bg = 0xFF112233.toInt(),
            fontId = "gloock", clockwise = false, align = EventAlign.END, itemShape = ItemShape.CIRCLE,
            futureStyle = FutureStyle.FADED, icon = "gift", barSize = ItemSize.LARGE, shape = TileShape.CIRCLE, showNameOutside = false,
        )
        assertEquals(o, EventOptions.decode(o.encode()))
    }

    @Test fun `defaults round trip`() {
        assertEquals(EventOptions(), EventOptions.decode(EventOptions().encode()))
        assertEquals(EventOptions(), EventOptions.decode("{}"))
    }

    @Test fun `unknown keys are ignored and missing ones default`() {
        val o = EventOptions.decode("""{"design":"SWISS","futureThing":{"a":1},"showPercent":true}""")
        assertEquals(EventDesign.SWISS, o.design)
        assertTrue(o.showPercent)
        assertEquals("inter", o.fontId)
    }

    @Test fun `garbage falls back to defaults without throwing`() {
        for (g in listOf("", "   ", "not json", "[1,2]", "{", "null", """{"design":5}""", """{"showPercent":"maybe"}""")) {
            val o = EventOptions.decode(g)
            assertFalse(g, o.showPercent)
        }
        assertEquals(EventOptions(), EventOptions.decode(null))
    }

    @Test fun `unknown enum value falls back to the default`() {
        val o = EventOptions.decode("""{"design":"HOLOGRAM","showPercent":true}""")
        assertEquals(EventDesign.FONTY, o.design)
        assertTrue(o.showPercent)
    }

    @Test fun `round up default depends on direction`() {
        assertTrue(EventOptions().roundUpFor(countUp = false))
        assertFalse(EventOptions().roundUpFor(countUp = true))
        assertFalse(EventOptions(roundUp = false).roundUpFor(countUp = false))
    }

    @Test fun `templates and palette are complete`() {
        assertEquals(8, EventTemplates.starters.size)
        assertTrue(EventTemplates.ideas.size in 55..70)
        assertEquals(40, EventDefaults.palette.size)
        assertTrue(EventDefaults.presets.size >= 10)
        assertEquals(EventTemplates.ideas.size, EventTemplates.ideas.map { it.name }.toSet().size)
        EventTemplates.ideas.forEach { assertTrue(it.name, it.options().bg == EventDefaults.preset(it.preset).bg) }
    }
}
