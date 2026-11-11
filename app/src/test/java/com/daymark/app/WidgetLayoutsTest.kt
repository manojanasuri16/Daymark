package com.daymark.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Widget layouts (previews and the initial/loading layout) are inflated as RemoteViews by the launcher, which only allows
 * a short list of classes. The Pixel launcher and the emulator are lenient, but some launchers reject the whole layout
 * ("Couldn't add widget" / "Can't load widget"). Keep every widget_*.xml to the allowed set.
 */
class WidgetLayoutsTest {
    private val allowed = setOf(
        "AdapterViewFlipper", "FrameLayout", "GridLayout", "GridView", "LinearLayout", "ListView", "RelativeLayout",
        "StackView", "ViewFlipper", "AnalogClock", "Button", "Chronometer", "ImageButton", "ImageView", "ProgressBar",
        "TextClock", "TextView",
    )

    @Test
    fun `widget layouts only use classes RemoteViews allows`() {
        val dir = listOf(File("src/main/res/layout"), File("app/src/main/res/layout")).first { it.isDirectory }
        val files = dir.listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith(".xml") }!!.sortedBy { it.name }
        assertTrue("no widget layouts found", files.isNotEmpty())
        val bad = mutableListOf<String>()
        for (f in files) {
            Regex("<([A-Za-z][A-Za-z0-9_.]*)").findAll(f.readText()).map { it.groupValues[1] }.forEach { tag ->
                if (tag !in allowed) bad += "${f.name}: <$tag>"
            }
        }
        assertTrue("Disallowed RemoteViews classes: $bad", bad.isEmpty())
    }

    @Test
    fun `widget layouts do not use theme attribute styles`() {
        val dir = listOf(File("src/main/res/layout"), File("app/src/main/res/layout")).first { it.isDirectory }
        val offenders = dir.listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith(".xml") }!!
            .filter { it.readText().contains("style=\"?") }.map { it.name }
        assertTrue("Theme-attribute styles can fail in the launcher's context: $offenders", offenders.isEmpty())
    }
}
