package com.daymark.app.ui.events

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.daymark.app.domain.CountEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Shares an event card as a PNG through the system share sheet (nothing is uploaded by Daymark). */
object EventShare {
    private const val W = 1200
    private const val H = 630
    private const val DENSITY = 3.2f

    suspend fun share(context: Context, event: CountEvent, now: Long = System.currentTimeMillis()) {
        val uri = withContext(Dispatchers.Default) {
            val bmp = EventArt.render(context, event, now, W, H, EventBackground.FULL, DENSITY)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "daymark-event.png")
            file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        }
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, "Share event").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
