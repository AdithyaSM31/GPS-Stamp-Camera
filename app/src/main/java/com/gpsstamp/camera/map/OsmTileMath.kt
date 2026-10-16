package com.gpsstamp.camera.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Pixel position on the whole Web Mercator map at a zoom level. */
data class WorldPixel(val x: Double, val y: Double)

/** Inclusive range of OSM tile indices. */
data class TileRange(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int)

object OsmTileMath {
    const val TILE_SIZE = 256

    fun worldPixel(lat: Double, lon: Double, zoom: Int): WorldPixel {
        val size = 2.0.pow(zoom) * TILE_SIZE
        val latRad = lat * PI / 180
        val x = (lon + 180) / 360 * size
        val y = (1 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2 * size
        return WorldPixel(x, y)
    }

    /** Tiles covering the [sizePx] square centred on [center], i.e. [c - size/2, c + size/2). */
    fun tileRange(center: WorldPixel, sizePx: Int): TileRange {
        val half = sizePx / 2.0
        fun tile(px: Double) = floor(px / TILE_SIZE).toInt()
        return TileRange(
            minX = tile(center.x - half),
            maxX = tile(center.x + half - 1),
            minY = tile(center.y - half),
            maxY = tile(center.y + half - 1),
        )
    }
}
