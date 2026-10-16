package com.gpsstamp.camera.stamp

import kotlin.math.max

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Stamp layout in pixels, relative to the stamp's own top-left corner. */
data class StampGeometry(
    val titleSize: Float,
    val bodySize: Float,
    val lineHeightFactor: Float,
    val padding: Float,
    val cornerRadius: Float,
    val map: Box?,
    val panel: Box,
    val badge: Box,
    val totalHeight: Float,
)

/** Every size is a fraction of the image width, so the preview and the full-resolution photo match. */
object StampGeometryCalc {
    private const val MARGIN = 0.02f
    private const val MAP_SIZE = 0.24f
    private const val PADDING = 0.022f
    private const val TITLE_SIZE = 0.040f
    private const val BODY_SIZE = 0.028f
    private const val LINE_HEIGHT = 1.25f
    private const val BADGE_HEIGHT = 0.034f
    private const val BADGE_WIDTH = 0.22f
    private const val CORNER_RADIUS = 0.015f

    private fun panelLeft(w: Float, hasMap: Boolean) = w * MARGIN + if (hasMap) w * MAP_SIZE + w * MARGIN else 0f

    fun textWidth(widthPx: Int, hasMap: Boolean): Float {
        val w = widthPx.toFloat()
        return (w - w * MARGIN) - panelLeft(w, hasMap) - 2 * w * PADDING
    }

    fun compute(widthPx: Int, hasMap: Boolean, bodyLineCount: Int): StampGeometry {
        val w = widthPx.toFloat()
        val margin = w * MARGIN
        val padding = w * PADDING
        val titleSize = w * TITLE_SIZE
        val bodySize = w * BODY_SIZE
        val mapSize = w * MAP_SIZE
        val badgeHeight = w * BADGE_HEIGHT

        val content = 2 * padding + LINE_HEIGHT * titleSize + bodyLineCount * LINE_HEIGHT * bodySize
        val panelHeight = if (hasMap) max(mapSize, content) else content
        val panelTop = margin + badgeHeight
        val panel = Box(panelLeft(w, hasMap), panelTop, w - margin, panelTop + panelHeight)
        val map = if (hasMap) Box(margin, panel.bottom - mapSize, margin + mapSize, panel.bottom) else null
        val badge = Box(panel.right - w * BADGE_WIDTH, margin, panel.right, panelTop)

        return StampGeometry(
            titleSize = titleSize,
            bodySize = bodySize,
            lineHeightFactor = LINE_HEIGHT,
            padding = padding,
            cornerRadius = w * CORNER_RADIUS,
            map = map,
            panel = panel,
            badge = badge,
            totalHeight = panel.bottom + margin,
        )
    }
}
