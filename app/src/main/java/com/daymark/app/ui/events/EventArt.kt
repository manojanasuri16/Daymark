package com.daymark.app.ui.events

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.daymark.app.domain.CountEvent
import com.daymark.app.domain.EventAlign
import com.daymark.app.domain.EventDesign
import com.daymark.app.domain.EventEngine
import com.daymark.app.domain.EventOptions
import com.daymark.app.domain.FutureStyle
import com.daymark.app.domain.ItemShape
import com.daymark.app.domain.ItemSize
import com.daymark.app.domain.TileShape
import com.daymark.app.ui.events.EventLayoutMath.Aspect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** How the card sits on the home screen / in the app. */
enum class EventBackground { FULL, TRANSLUCENT, TRANSPARENT }

/**
 * The one Canvas renderer of event cards: the in-app cards, the editor preview and the widget all call [render],
 * so what you design is exactly what the widget shows. Pure function of its inputs (no I/O besides cached fonts).
 */
object EventArt {
    private const val TRANSLUCENT_ALPHA = 0.55f

    fun render(
        context: Context,
        event: CountEvent,
        now: Long,
        widthPx: Int,
        heightPx: Int,
        background: EventBackground,
        density: Float,
        hideName: Boolean = false,
        precision: Boolean = true,
    ): Bitmap {
        val w = max(widthPx, 1)
        val h = max(heightPx, 1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val o = event.options
        val progress = EventEngine.progress(event, now)
        val text = EventEngine.format(event, now, precision = precision)
        val st = Style(context, o, density, background, w.toFloat(), h.toFloat())

        drawCard(canvas, st, background)
        val title = if (hideName) "Event" else event.name.ifBlank { "Untitled" }
        val data = Data(title, text.primary, text.detailDate, progress.fraction, progress.totalMs)
        // inset by the padding; everything is clipped to the card content
        val c = RectF(st.pad, st.pad, w - st.pad, h - st.pad)
        when (o.design) {
            EventDesign.FONTY -> drawFonty(canvas, st, data, c)
            EventDesign.SWISS -> drawSwiss(canvas, st, data, c)
            EventDesign.GRID -> drawItems(canvas, st, data, c, bars = false)
            EventDesign.ROW -> drawItems(canvas, st, data, c, bars = true)
            EventDesign.MINIMAL -> drawMinimal(canvas, st, data, c)
        }
        return bmp
    }

    /** Text for accessibility: "name, primary line". */
    fun describe(event: CountEvent, now: Long, hideName: Boolean = false): String {
        val t = EventEngine.format(event, now)
        return if (hideName) t.primary else "${event.name}, ${t.primary}"
    }

    // ---- model ----

    private class Data(val title: String, val primary: String, val detail: String, val fraction: Float?, val totalMs: Long?)

    private class Style(
        val context: Context,
        val o: EventOptions,
        val density: Float,
        val bgMode: EventBackground,
        val w: Float,
        val h: Float,
    ) {
        val wDp = w / density
        val hDp = h / density
        val aspect = EventLayoutMath.aspectOf(w, h)
        val bg = o.bg
        val accent = o.accent
        val text = o.text
        val pad = dp(min(14f, max(8f, min(wDp, hDp) * 0.13f)))
        val radius = min(dp(24f), min(w, h) * 0.3f)
        val titleTf: Typeface = EventFonts.weighted(context, o.fontId, 700)
        val bodyTf: Typeface = EventFonts.weighted(context, o.fontId, 500)
        val lightTf: Typeface = EventFonts.weighted(context, o.fontId, 400)

        /** Base text size grows with the card: ~12dp on 2x1 up to ~26dp on big cards. */
        val baseDp = (0.115f * sqrt(wDp * hDp)).coerceIn(11f, 26f)
        val primaryColor = alpha(text, 0.62f)
        val detailColor = alpha(text, 0.55f)

        /** Track colour for rings, bars and empty items; stays visible on any wallpaper. */
        val track: Int = when (bgMode) {
            EventBackground.TRANSPARENT -> alpha(text, 0.22f)
            else -> if (luminance(bg) > 0.5f) mix(bg, Color.BLACK, 0.12f) else mix(bg, Color.WHITE, 0.16f)
        }

        fun dp(v: Float) = v * density
    }

    // ---- card ----

    private fun drawCard(canvas: Canvas, st: Style, mode: EventBackground) {
        if (mode == EventBackground.TRANSPARENT) return
        val r = RectF(0f, 0f, st.w, st.h)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = if (mode == EventBackground.TRANSLUCENT) alpha(st.bg, TRANSLUCENT_ALPHA) else st.bg
        canvas.drawRoundRect(r, st.radius, st.radius, fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
        stroke.style = Paint.Style.STROKE
        val sw = max(st.dp(1f), 1f)
        stroke.strokeWidth = sw
        val line = if (luminance(st.bg) > 0.55f) mix(st.bg, Color.BLACK, 0.07f) else mix(st.bg, Color.WHITE, 0.14f)
        stroke.color = if (mode == EventBackground.TRANSLUCENT) alpha(line, 0.7f) else line
        canvas.drawRoundRect(RectF(sw / 2, sw / 2, st.w - sw / 2, st.h - sw / 2), st.radius - sw / 2, st.radius - sw / 2, stroke)
    }

    // ---- text helpers ----

    private fun paint(tf: Typeface, color: Int, sizePx: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).also {
        it.typeface = tf; it.color = color; it.textSize = sizePx
    }

    private fun alignment(a: EventAlign) = when (a) {
        EventAlign.START -> Layout.Alignment.ALIGN_NORMAL
        EventAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
        EventAlign.END -> Layout.Alignment.ALIGN_OPPOSITE
    }

    private fun build(text: String, p: TextPaint, width: Int, a: Layout.Alignment, maxLines: Int, ellipsize: Boolean): StaticLayout {
        val b = StaticLayout.Builder.obtain(text, 0, text.length, p, max(width, 1))
            .setAlignment(a).setIncludePad(false).setLineSpacing(0f, 1f)
        if (ellipsize) b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
        return b.build()
    }

    /** Largest text size (down to [minPx]) whose wrapped text stays within [maxLines] lines and [maxH]; ellipsised if even the minimum is too big. */
    private fun fit(
        text: String, p: TextPaint, width: Float, maxLines: Int, minPx: Float, maxPx: Float, maxH: Float, a: Layout.Alignment,
    ): StaticLayout {
        val wi = max(width.toInt(), 1)
        val size = EventLayoutMath.fitSize(minPx, maxPx) { s ->
            p.textSize = s
            val l = build(text, p, wi, a, Int.MAX_VALUE, false)
            l.lineCount <= maxLines && l.height <= maxH
        }
        p.textSize = size
        return build(text, p, wi, a, maxLines, true)
    }

    private fun draw(canvas: Canvas, l: StaticLayout, x: Float, y: Float) {
        canvas.save(); canvas.translate(x, y); l.draw(canvas); canvas.restore()
    }

    /**
     * Title with the primary line under it inside [r]; returns the height used. [primaryScale] lets a design give the
     * primary line more weight than the title (big text cards).
     */
    private fun drawHeader(
        canvas: Canvas, st: Style, d: Data, r: RectF, a: Layout.Alignment,
        titleLines: Int, primaryScale: Float = 1f, primaryLines: Int = 2,
    ): Float {
        val gap = st.dp(2f)
        val titleMax = st.dp(st.baseDp * 1.05f)
        val titleMin = st.dp(10f)
        val tl = fit(d.title, paint(st.titleTf, st.text, titleMax), r.width(), titleLines, titleMin, titleMax, r.height() * 0.62f, a)
        draw(canvas, tl, r.left, r.top)
        val used = tl.height + gap
        val primMax = st.dp(st.baseDp * primaryScale)
        val pl = fit(d.primary, paint(st.bodyTf, st.primaryColor, primMax), r.width(), primaryLines, st.dp(9f), primMax, r.height() - used, a)
        draw(canvas, pl, r.left, r.top + used)
        return used + pl.height
    }

    private fun drawDetail(canvas: Canvas, st: Style, d: Data, r: RectF, a: Layout.Alignment): Float {
        val size = st.dp(max(9f, st.baseDp * 0.68f))
        val l = fit(d.detail, paint(st.lightTf, st.detailColor, size), r.width(), 1, st.dp(8f), size, r.height(), a)
        draw(canvas, l, r.left, r.bottom - l.height)
        return l.height.toFloat()
    }

    // ---- designs ----

    private fun drawFonty(canvas: Canvas, st: Style, d: Data, c: RectF) {
        val a = alignment(st.o.align)
        val roomy = st.aspect != Aspect.WIDE && c.height() >= st.dp(70f)
        val showDetail = st.o.showDetails && c.height() >= st.dp(60f)
        var bottom = c.bottom
        if (showDetail) bottom -= drawDetail(canvas, st, d, c, a) + st.dp(4f)
        val head = RectF(c.left, c.top, c.right, bottom)
        drawHeader(canvas, st, d, head, a, titleLines = if (c.height() >= st.dp(110f)) 2 else 1, primaryScale = if (roomy) 1.55f else 1f)
    }

    private fun ringGeometry(st: Style, c: RectF): Triple<Float, Float, Float> {
        // diameter, centre x, centre y
        val dia = when (st.aspect) {
            Aspect.WIDE -> min(c.height(), c.width() * 0.4f)
            else -> min(c.width() * 0.6f, c.height() * 0.52f)
        }
        return Triple(dia, c.right - dia / 2f, c.bottom - dia / 2f)
    }

    private fun drawSwiss(canvas: Canvas, st: Style, d: Data, c: RectF) {
        val (dia, cx, cy) = ringGeometry(st, c)
        val gap = st.dp(8f)
        val stroke = dia * 0.16f
        val oval = RectF(cx - dia / 2 + stroke / 2, cy - dia / 2 + stroke / 2, cx + dia / 2 - stroke / 2, cy + dia / 2 - stroke / 2)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = st.track }
        canvas.drawArc(oval, 0f, 360f, false, tp)
        val (start, sweep) = EventLayoutMath.ringArc(d.fraction ?: 1f, st.o.clockwise)
        val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; color = st.accent; strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(oval, start, sweep, false, ap)

        if (st.aspect == Aspect.WIDE) {
            val tr = RectF(c.left, c.top, cx - dia / 2 - gap, c.bottom)
            drawHeader(canvas, st, d, tr, Layout.Alignment.ALIGN_NORMAL, titleLines = 1)
        } else {
            // text over the ring, detail date bottom-left beside it
            val tr = RectF(c.left, c.top, c.right, cy - dia / 2 - gap)
            val head = if (tr.height() < st.dp(24f)) RectF(c.left, c.top, c.right, c.top + st.dp(24f)) else tr
            drawHeader(canvas, st, d, head, Layout.Alignment.ALIGN_NORMAL, titleLines = if (c.height() >= st.dp(150f)) 2 else 1)
            if (st.o.showDetails) {
                val dr = RectF(c.left, c.top, cx - dia / 2 - gap, c.bottom)
                if (dr.width() > st.dp(40f)) drawDetail(canvas, st, d, dr, Layout.Alignment.ALIGN_NORMAL)
            }
        }
    }

    private fun drawItems(canvas: Canvas, st: Style, d: Data, c: RectF, bars: Boolean) {
        val o = st.o
        val wide = st.aspect == Aspect.WIDE
        val gapOuter = st.dp(8f)
        val head: RectF
        val area: RectF
        if (wide) {
            val hw = c.width() * 0.42f
            head = RectF(c.left, c.top, c.left + hw, c.bottom)
            area = RectF(c.left + hw + gapOuter, c.top, c.right, c.bottom)
        } else {
            val headH = min(c.height() * 0.5f, st.dp(st.baseDp * 3.1f))
            head = RectF(c.left, c.top, c.right, c.top + headH)
            area = RectF(c.left, c.top + headH + gapOuter, c.right, c.bottom)
        }
        val headUsed = drawHeader(canvas, st, d, head, Layout.Alignment.ALIGN_NORMAL, titleLines = 1, primaryScale = 0.95f, primaryLines = if (wide) 3 else 1)
        val detailSize = st.dp(max(9f, st.baseDp * 0.62f))
        val detailH = detailSize * 1.3f
        if (!wide && o.showDetails && area.height() > st.dp(60f) && headUsed + detailH + st.dp(2f) <= head.height()) {
            // detail date on its own line at the foot of the header, so it never overlaps the title
            val dr = RectF(head.left, head.bottom - detailH, head.right, head.bottom)
            val l = fit(d.detail, paint(st.lightTf, st.detailColor, detailSize), dr.width(), 1, st.dp(8f), detailSize, dr.height() * 2, Layout.Alignment.ALIGN_NORMAL)
            draw(canvas, l, dr.left, dr.top)
        }
        if (area.width() < st.dp(12f) || area.height() < st.dp(8f)) return

        val ratio = when (o.itemSize) { ItemSize.SMALL -> 0.68f; ItemSize.MEDIUM -> 0.8f; ItemSize.LARGE -> 0.92f }
        val itemPaintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.accent }
        val itemFade = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(st.accent, 0.28f) }
        val itemOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = alpha(st.accent, 0.75f); strokeWidth = max(st.dp(1.2f), 1f)
        }

        fun drawItem(rect: RectF, filled: Boolean) {
            val rx = when (o.itemShape) {
                ItemShape.SQUARE -> min(rect.width(), rect.height()) * 0.12f
                ItemShape.ROUNDED -> min(rect.width(), rect.height()) * 0.32f
                ItemShape.CIRCLE -> min(rect.width(), rect.height()) / 2f
            }
            if (filled) { canvas.drawRoundRect(rect, rx, rx, itemPaintFill); return }
            when (o.futureStyle) {
                FutureStyle.HIDDEN -> Unit
                FutureStyle.FADED -> canvas.drawRoundRect(rect, rx, rx, itemFade)
                FutureStyle.OUTLINED -> {
                    val i = itemOutline.strokeWidth / 2
                    canvas.drawRoundRect(RectF(rect.left + i, rect.top + i, rect.right - i, rect.bottom - i), rx, rx, itemOutline)
                }
            }
        }

        if (bars) {
            val gap = st.dp(if (o.itemSize == ItemSize.LARGE) 4f else 3f)
            val minPitch = st.dp(5f) + gap
            val maxCount = max(((area.width() + gap) / minPitch).toInt(), 1)
            val count = EventLayoutMath.itemCount(d.totalMs, o.matchItems, o.itemSize, true, maxCount)
            val filled = EventLayoutMath.filledCount(d.fraction, count)
            val bw = (area.width() - (count - 1) * gap) / count
            val bh = area.height() * (if (o.itemSize == ItemSize.SMALL) 0.7f else if (o.itemSize == ItemSize.MEDIUM) 0.85f else 1f)
            val top = area.bottom - bh
            for (i in 0 until count) {
                val x = area.left + i * (bw + gap)
                drawItem(RectF(x, top, x + bw, area.bottom), EventLayoutMath.isFilled(i, count, filled, o.clockwise))
            }
        } else {
            val gap = st.dp(if (o.itemSize == ItemSize.SMALL) 2f else 3f)
            val minCell = st.dp(if (o.itemSize == ItemSize.LARGE) 12f else 6f)
            val maxCount = max(((area.width() / (minCell + gap)) * (area.height() / (minCell + gap))).toInt(), 1)
            val count = EventLayoutMath.itemCount(d.totalMs, o.matchItems, o.itemSize, false, maxCount)
            val filled = EventLayoutMath.filledCount(d.fraction, count)
            val g = EventLayoutMath.bestGrid(area.width(), area.height(), count, gap)
            val pitch = g.cell + gap
            val gridW = g.cols * g.cell + (g.cols - 1) * gap
            val gridH = g.rows * g.cell + (g.rows - 1) * gap
            val x0 = area.left + (area.width() - gridW) / 2f
            val y0 = area.top + (area.height() - gridH) / 2f
            val size = g.cell * ratio
            val inset = (g.cell - size) / 2f
            for (i in 0 until count) {
                val x = x0 + (i % g.cols) * pitch + inset
                val y = y0 + (i / g.cols) * pitch + inset
                drawItem(RectF(x, y, x + size, y + size), EventLayoutMath.isFilled(i, count, filled, o.clockwise))
            }
        }
    }

    private fun drawMinimal(canvas: Canvas, st: Style, d: Data, c: RectF) {
        val o = st.o
        val wide = st.aspect == Aspect.WIDE
        val barH = if (o.showBar) st.dp(when (o.barSize) { ItemSize.SMALL -> 4f; ItemSize.MEDIUM -> 7f; ItemSize.LARGE -> 11f }) else 0f
        val barGap = if (o.showBar) st.dp(8f) else 0f
        val body = RectF(c.left, c.top, c.right, c.bottom - barH - barGap)

        if (o.showBar) {
            val r = barH / 2f
            val bar = RectF(c.left, c.bottom - barH, c.right, c.bottom)
            canvas.drawRoundRect(bar, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(st.text, 0.2f) })
            val f = (d.fraction ?: 1f).coerceIn(0f, 1f)
            val fw = max(bar.width() * f, if (f > 0f) barH else 0f)
            val fillRect = if (o.clockwise) RectF(bar.left, bar.top, bar.left + fw, bar.bottom) else RectF(bar.right - fw, bar.top, bar.right, bar.bottom)
            if (fw > 0f) canvas.drawRoundRect(fillRect, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.accent })
        }

        val tileSize = if (o.showIcon) {
            if (wide) min(body.height(), st.dp(44f)) else min(st.dp(48f), max(st.dp(28f), body.height() * 0.3f)).coerceAtMost(body.width() * 0.35f)
        } else 0f
        if (o.showIcon && tileSize > 0f) {
            val tile = RectF(body.left, body.top, body.left + tileSize, body.top + tileSize)
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.accent }
            val rr = when (o.shape) { TileShape.SQUARE -> tileSize * 0.12f; TileShape.ROUNDED -> tileSize * 0.3f; TileShape.CIRCLE -> tileSize / 2f }
            canvas.drawRoundRect(tile, rr, rr, tp)
            val inset = tileSize * 0.22f
            EventIcons.draw(canvas, o.icon, RectF(tile.left + inset, tile.top + inset, tile.right - inset, tile.bottom - inset), o.iconColor)
        }

        val a = Layout.Alignment.ALIGN_NORMAL
        val textRect = if (wide && o.showIcon) {
            RectF(body.left + tileSize + st.dp(10f), body.top, body.right, body.bottom)
        } else {
            val top = if (o.showIcon) body.top + tileSize + st.dp(8f) else body.top
            RectF(body.left, top, body.right, body.bottom)
        }
        if (textRect.height() < st.dp(12f) || textRect.width() < st.dp(20f)) return
        drawHeader(canvas, st, d, textRect, a, titleLines = if (textRect.height() >= st.dp(70f)) 2 else 1, primaryScale = if (wide) 1f else 1.15f)
        if (o.showDetails && !wide && textRect.height() >= st.dp(70f)) drawDetail(canvas, st, d, body, a)
    }

    // ---- colour helpers ----

    private fun alpha(color: Int, a: Float): Int = Color.argb((Color.alpha(color) * a).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun mix(from: Int, to: Int, t: Float): Int = Color.argb(
        Color.alpha(from),
        (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt(),
    )

    private fun luminance(c: Int): Float = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
}
