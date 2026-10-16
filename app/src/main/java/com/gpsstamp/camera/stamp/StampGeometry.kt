package com.gpsstamp.camera.stamp

import kotlin.math.max
import kotlin.math.min

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

/**
 * Every size is a fraction of [scaleBasis], so the preview and the full-resolution photo match, and the stamp
 * covers the same share of the height in landscape as in portrait. The panel still spans the full width.
 */
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

    // A 3:4 portrait photo's width; for a 4:3 landscape photo, the width it would have upright.
    private const val BASIS_HEIGHT_RATIO = 0.75f

    /** Portrait photos scale by their width (unchanged); landscape ones by ¾ of their height. */
    fun scaleBasis(widthPx: Int, heightPx: Int): Float = min(widthPx.toFloat(), heightPx * BASIS_HEIGHT_RATIO)

    private fun panelLeft(s: Float, hasMap: Boolean) = s * MARGIN + if (hasMap) s * MAP_SIZE + s * MARGIN else 0f

    fun textWidth(widthPx: Int, heightPx: Int, hasMap: Boolean): Float {
        val s = scaleBasis(widthPx, heightPx)
        return (widthPx - s * MARGIN) - panelLeft(s, hasMap) - 2 * s * PADDING
    }

    fun compute(widthPx: Int, heightPx: Int, hasMap: Boolean, bodyLineCount: Int): StampGeometry {
        val w = widthPx.toFloat()
        val s = scaleBasis(widthPx, heightPx)
        val margin = s * MARGIN
        val padding = s * PADDING
        val titleSize = s * TITLE_SIZE
        val bodySize = s * BODY_SIZE
        val mapSize = s * MAP_SIZE
        val badgeHeight = s * BADGE_HEIGHT

        val content = 2 * padding + LINE_HEIGHT * titleSize + bodyLineCount * LINE_HEIGHT * bodySize
        val panelHeight = if (hasMap) max(mapSize, content) else content
        val panelTop = margin + badgeHeight
        val panel = Box(panelLeft(s, hasMap), panelTop, w - margin, panelTop + panelHeight)
        val map = if (hasMap) Box(margin, panel.bottom - mapSize, margin + mapSize, panel.bottom) else null
        val badge = Box(panel.right - s * BADGE_WIDTH, margin, panel.right, panelTop)

        return StampGeometry(
            titleSize = titleSize,
            bodySize = bodySize,
            lineHeightFactor = LINE_HEIGHT,
            padding = padding,
            cornerRadius = s * CORNER_RADIUS,
            map = map,
            panel = panel,
            badge = badge,
            totalHeight = panel.bottom + margin,
        )
    }
}
