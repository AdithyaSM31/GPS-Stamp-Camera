package com.gpsstamp.camera.stamp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.ceil

private const val BADGE_TEXT = "GPS Stamp Camera"
private const val ADDRESS_MAX_LINES = 2

/** Draws the stamp into a transparent bitmap as wide as the target image ([widthPx] x [heightPx]); callers place it at the image bottom. */
object StampRenderer {
    fun render(data: StampData, widthPx: Int, heightPx: Int): Bitmap {
        val hasMap = data.map != null
        val text = StampFormatter.buildText(
            data.lat, data.lon, data.accuracyM, data.altitudeM, data.timeMs, data.zone, data.address, data.note
        )
        val sizes = StampGeometryCalc.compute(widthPx, heightPx, hasMap, bodyLineCount = 0)
        val titlePaint = textPaint(sizes.titleSize, bold = true)
        val bodyPaint = textPaint(sizes.bodySize, bold = false)

        val maxText = StampGeometryCalc.textWidth(widthPx, heightPx, hasMap)
        val title = TextFit.ellipsize(text.title, maxText, titlePaint::measureText)
        val body = text.body.flatMapIndexed { i, line ->
            if (i == 0 && data.address != null) TextFit.wrap(line, maxText, ADDRESS_MAX_LINES, bodyPaint::measureText)
            else listOf(TextFit.ellipsize(line, maxText, bodyPaint::measureText))
        }

        val g = StampGeometryCalc.compute(widthPx, heightPx, hasMap, body.size)
        val bitmap = Bitmap.createBitmap(widthPx, ceil(g.totalHeight).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPanel(canvas, g)
        data.map?.let { drawMap(canvas, it, g) }
        drawText(canvas, g, title, body, titlePaint, bodyPaint)
        drawBadge(canvas, g)
        return bitmap
    }

    private fun textPaint(size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = size
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        setShadowLayer(size * 0.08f, 0f, size * 0.04f, Color.argb(160, 0, 0, 0))
    }

    private fun Box.toRectF() = RectF(left, top, right, bottom)

    private fun drawPanel(canvas: Canvas, g: StampGeometry) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(153, 0, 0, 0) }
        canvas.drawRoundRect(g.panel.toRectF(), g.cornerRadius, g.cornerRadius, paint)
    }

    private fun drawMap(canvas: Canvas, map: Bitmap, g: StampGeometry) {
        val dst = (g.map ?: return).toRectF()
        val clip = Path().apply { addRoundRect(dst, g.cornerRadius, g.cornerRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(map, Rect(0, 0, map.width, map.height), dst, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    private fun drawText(
        canvas: Canvas,
        g: StampGeometry,
        title: String,
        body: List<String>,
        titlePaint: Paint,
        bodyPaint: Paint,
    ) {
        val content = g.lineHeightFactor * (g.titleSize + body.size * g.bodySize)
        val x = g.panel.left + g.padding
        // Centre the text block when the panel is stretched to the map's height.
        var y = g.panel.top + (g.panel.height - content) / 2
        canvas.drawText(title, x, y + g.titleSize, titlePaint)
        y += g.lineHeightFactor * g.titleSize
        for (line in body) {
            canvas.drawText(line, x, y + g.bodySize, bodyPaint)
            y += g.lineHeightFactor * g.bodySize
        }
    }

    private fun drawBadge(canvas: Canvas, g: StampGeometry) {
        val box = g.badge.toRectF()
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(204, 30, 30, 30) }
        canvas.drawRoundRect(box, box.height() / 2, box.height() / 2, bg)
        val paint = textPaint(box.height() * 0.55f, bold = true).apply { textAlign = Paint.Align.CENTER }
        val baseline = box.centerY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(TextFit.ellipsize(BADGE_TEXT, box.width() * 0.9f, paint::measureText), box.centerX(), baseline, paint)
    }
}
