package com.gpsstamp.camera.map

import org.junit.Assert.assertEquals
import org.junit.Test

class OsmTileMathTest {
    @Test fun worldPixel_vit() {
        val p = OsmTileMath.worldPixel(12.843552, 80.154484, 16)
        assertEquals(12124077.698, p.x, 0.01)
        assertEquals(7784978.436, p.y, 0.01)
        assertEquals(47359, (p.x / 256).toInt())
        assertEquals(30410, (p.y / 256).toInt())
    }

    @Test fun worldPixel_origin() {
        val p = OsmTileMath.worldPixel(0.0, 0.0, 16)
        assertEquals(8388608.0, p.x, 1e-6)
        assertEquals(8388608.0, p.y, 1e-6)
    }

    @Test fun worldPixel_southEast() {
        val p = OsmTileMath.worldPixel(-33.856784, 151.215297, 16)
        assertEquals(60295, (p.x / 256).toInt())
        assertEquals(39325, (p.y / 256).toInt())
    }

    @Test fun tileRange_straddlesFourTiles() =
        assertEquals(TileRange(32767, 32768, 32767, 32768), OsmTileMath.tileRange(WorldPixel(8388608.0, 8388608.0), 256))

    @Test fun tileRange_singleTile() =
        assertEquals(
            TileRange(47359, 47359, 30410, 30410),
            OsmTileMath.tileRange(WorldPixel(47359 * 256 + 128.0, 30410 * 256 + 128.0), 256)
        )
}
