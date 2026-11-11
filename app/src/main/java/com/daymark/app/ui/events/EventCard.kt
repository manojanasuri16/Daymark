package com.daymark.app.ui.events

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.daymark.app.domain.CountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf

/** Typical widget / card sizes for previews and the editor's size switcher. */
object EventCardPreviewSizes {
    val W2xH1 = DpSize(160.dp, 70.dp)
    val W2xH2 = DpSize(160.dp, 160.dp)
    val W4xH2 = DpSize(320.dp, 150.dp)
    val W4xH3 = DpSize(320.dp, 220.dp)
    val all: List<Pair<String, DpSize>> = listOf("2x1" to W2xH1, "2x2" to W2xH2, "4x2" to W4xH2, "4x3" to W4xH3)
}

/** Current time that ticks once a minute, for cards that show elapsed/remaining text. */
@Composable
fun rememberMinuteNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            delay(60_000L - t % 60_000L + 50L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * Shows an event exactly as [EventArt] renders it (same pixels as the widget). Give it a size via [modifier]
 * (fixed size or aspect ratio + width); it re-renders when the event, size, minute or background change.
 */
@Composable
fun EventCard(
    event: CountEvent,
    now: Long,
    modifier: Modifier = Modifier,
    background: EventBackground = EventBackground.FULL,
    hideName: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val minute = now / 60_000L
    val bitmap by produceState<ImageBitmap?>(null, event, size, minute, background, hideName, density) {
        if (size.width <= 0 || size.height <= 0) return@produceState
        value = withContext(Dispatchers.Default) {
            EventArt.render(context, event, minute * 60_000L, size.width, size.height, background, density, hideName).asImageBitmap()
        }
    }
    val desc = remember(event, minute, hideName) { EventArt.describe(event, minute * 60_000L, hideName) }
    val img = bitmap
    androidx.compose.foundation.layout.Box(modifier.onSizeChanged { size = it }.semantics { contentDescription = desc }) {
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize())
        }
    }
}
