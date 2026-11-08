package com.daymark.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daymark.app.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a stored journal photo off the main thread at roughly [maxEdge] pixels. */
@Composable
private fun rememberPhoto(name: String, maxEdgePx: Int): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(name, maxEdgePx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(name, maxEdgePx) {
        bitmap = withContext(Dispatchers.IO) { PhotoStore.thumbnail(context, name, maxEdgePx)?.asImageBitmap() }
    }
    return bitmap
}

/** A rounded thumbnail. [onRemove] shows a small remove button; [onClick] opens it. */
@Composable
fun PhotoThumb(name: String, size: Dp, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    val px = with(LocalDensity.current) { size.roundToPx() } * 2
    val bmp = rememberPhoto(name, px)
    Box(Modifier.size(size)) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
        ) {
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (onRemove != null) {
            Surface(
                shape = CircleShape, color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clickable(onClick = onRemove),
            ) { Icon(Icons.Rounded.Close, "Remove photo", tint = Color.White, modifier = Modifier.padding(4.dp)) }
        }
    }
}

/** Full-screen photo viewer; tap anywhere to close. */
@Composable
fun PhotoViewer(name: String, onDismiss: () -> Unit) {
    val bmp = rememberPhoto(name, 2048)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, "Journal photo", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
        }
    }
}
