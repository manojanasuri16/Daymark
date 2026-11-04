package com.daymark.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.daymark.app.domain.DayStatus
import java.time.LocalDate

/** Glance can't draw arcs or dense grids, so these are rendered as small bitmaps. */
object WidgetArt {

    /** Progress ring with a round cap and a soft glow dot at the leading edge. */
    fun ring(sizePx: Int, progress: Float, palette: WidgetStyle.Palette, strokeFraction: Float = 0.085f): Bitmap {
        val size = sizePx.coerceIn(64, 720)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = size * strokeFraction
        val inset = stroke / 2f + size * 0.02f
        val rect = RectF(inset, inset, size - inset, size - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = palette.track
        c.drawArc(rect, 0f, 360f, false, paint)
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            paint.color = palette.accent
            c.drawArc(rect, -90f, 360f * p.coerceAtLeast(0.004f), false, paint)
            // Leading-edge "sun" dot.
            val angle = Math.toRadians((-90.0 + 360.0 * p))
            val r = rect.width() / 2f
            val cx = rect.centerX() + (r * Math.cos(angle)).toFloat()
            val cy = rect.centerY() + (r * Math.sin(angle)).toFloat()
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.text }
            c.drawCircle(cx, cy, stroke * 0.32f, dot)
        }
        return bmp
    }

    /**
     * GitHub-style grid: one column per week (oldest left), Monday-first rows.
     * The final column ends at [today]; future cells are left empty.
     */
    fun heatmap(
        widthPx: Int,
        heightPx: Int,
        weeks: Int,
        today: LocalDate,
        status: (LocalDate) -> DayStatus,
        palette: WidgetStyle.Palette,
    ): Bitmap {
        val w = widthPx.coerceIn(64, 1400)
        val h = heightPx.coerceIn(32, 700)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cols = weeks.coerceAtLeast(1)
        val gapRatio = 0.22f
        val cell = minOf(w / (cols + (cols - 1) * gapRatio), h / (7 + 6 * gapRatio))
        val gap = cell * gapRatio
        val gridW = cols * cell + (cols - 1) * gap
        val gridH = 7 * cell + 6 * gap
        val ox = (w - gridW) / 2f
        val oy = (h - gridH) / 2f
        val radius = cell * 0.28f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = maxOf(1.5f, cell * 0.12f); color = palette.text
        }
        val todayRow = today.dayOfWeek.value - 1 // Monday = 0
        val lastMonday = today.minusDays(todayRow.toLong())
        for (col in 0 until cols) {
            val monday = lastMonday.minusWeeks((cols - 1 - col).toLong())
            for (row in 0 until 7) {
                val date = monday.plusDays(row.toLong())
                if (date.isAfter(today)) continue
                paint.color = when (status(date)) {
                    DayStatus.CLEAN -> palette.clean
                    DayStatus.RELAPSE -> palette.relapse
                    DayStatus.UNTRACKED -> palette.untracked
                }
                val x = ox + col * (cell + gap)
                val y = oy + row * (cell + gap)
                val r = RectF(x, y, x + cell, y + cell)
                c.drawRoundRect(r, radius, radius, paint)
                if (date == today) {
                    val inset = ring.strokeWidth / 2f
                    c.drawRoundRect(RectF(r.left - inset * 2, r.top - inset * 2, r.right + inset * 2, r.bottom + inset * 2), radius * 1.3f, radius * 1.3f, ring)
                }
            }
        }
        return bmp
    }

    /** Thin rounded progress bar (Glance's LinearProgressIndicator can't be styled per theme reliably). */
    fun bar(widthPx: Int, heightPx: Int, progress: Float, palette: WidgetStyle.Palette): Bitmap {
        val w = widthPx.coerceIn(16, 1400)
        val h = heightPx.coerceIn(2, 64)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = h / 2f
        paint.color = palette.track
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            paint.color = palette.accent
            c.drawRoundRect(RectF(0f, 0f, maxOf(h.toFloat(), w * p), h.toFloat()), r, r, paint)
        }
        return bmp
    }
}
