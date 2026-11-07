package com.daymark.app.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.daymark.app.ui.theme.Accent
import java.io.File

/**
 * A milestone image, drawn locally and handed to the system share sheet. Nothing is uploaded by Daymark;
 * the user chooses where (if anywhere) it goes. By default the card names no habit — only the number of days.
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1350

    fun render(days: Long, title: String, subtitle: String, label: String?, accent: Accent): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), accent.heroLight.first.toInt(), accent.heroLight.second.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // Soft ring
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 26f; strokeCap = Paint.Cap.ROUND; color = 0x2EFFFFFF
        }
        val r = RectF(W / 2f - 340f, 230f, W / 2f + 340f, 910f)
        c.drawOval(r, ring)
        ring.color = 0xEBFFFFFF.toInt()
        c.drawArc(r, -90f, 300f, false, ring)

        fun text(s: String, size: Float, y: Float, alpha: Int, bold: Boolean = false) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (alpha shl 24) or 0xFFFFFF
                textSize = size
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            c.drawText(s, W / 2f, y, p)
        }

        text(days.toString(), if (days >= 1000) 230f else 300f, 640f, 0xFF, bold = true)
        text(if (days == 1L) "day" else "days", 64f, 740f, 0xD9)
        text(title, 76f, 1060f, 0xFF, bold = true)
        text(subtitle, 42f, 1130f, 0xCC)
        label?.takeIf { it.isNotBlank() }?.let { text(it, 40f, 160f, 0xCC) }
        text("Daymark", 36f, 1280f, 0x99)
        return bmp
    }

    fun share(context: Context, bitmap: Bitmap) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "daymark-milestone.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        context.startActivity(Intent.createChooser(send, "Share milestone").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
