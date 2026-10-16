package com.gpsstamp.camera.stamp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StampGeometryTest {
    private val d = 0.01f

    // Portrait 3:4-ish frame whose scale basis is its width (1000).
    private val pw = 1000
    private val ph = 1400

    @Test fun scaleBasis_portraitUsesWidth() = assertEquals(3024f, StampGeometryCalc.scaleBasis(3024, 4032), d)

    @Test fun scaleBasis_landscapeUsesThreeQuartersOfHeight() =
        assertEquals(2268f, StampGeometryCalc.scaleBasis(4032, 3024), d)

    @Test fun withMap_mapIsSquareAndPanelIsAtLeastMapHeight() {
        val g = StampGeometryCalc.compute(pw, ph, hasMap = true, bodyLineCount = 4)
        val map = g.map!!
        assertEquals(240f, map.right - map.left, d)
        assertEquals(240f, map.bottom - map.top, d)
        assertEquals(280f, g.panel.left, d)
        assertEquals(980f, g.panel.right, d)
        assertEquals(240f, g.panel.bottom - g.panel.top, d) // content 44 + 50 + 140 = 234 < 240
        assertEquals(map.bottom, g.panel.bottom, d)
        assertEquals(20f + 34f + 240f + 20f, g.totalHeight, d)
    }

    @Test fun withoutMap_panelSpansFullWidthAndGrowsWithContent() {
        val g = StampGeometryCalc.compute(pw, ph, hasMap = false, bodyLineCount = 6)
        assertNull(g.map)
        assertEquals(20f, g.panel.left, d)
        assertEquals(44f + 50f + 210f, g.panel.bottom - g.panel.top, d)
    }

    @Test fun scalesLinearlyWithSize() = assertEquals(
        2 * StampGeometryCalc.compute(1000, 1400, true, 4).totalHeight,
        StampGeometryCalc.compute(2000, 2800, true, 4).totalHeight,
        d
    )

    @Test fun textWidth_isPanelWidthMinusPadding() =
        assertEquals(980f - 280f - 2 * 22f, StampGeometryCalc.textWidth(pw, ph, hasMap = true), d)

    @Test fun landscape_stampIsShorterButSpansFullWidth() {
        // 1333x1000 landscape: basis 750, so the stamp keeps the same share of the height as a portrait photo.
        val g = StampGeometryCalc.compute(1333, 1000, hasMap = true, bodyLineCount = 4)
        assertEquals(180f, g.map!!.right - g.map!!.left, d)
        assertEquals(210f, g.panel.left, d)
        assertEquals(1318f, g.panel.right, d)
        assertEquals(15f + 25.5f + 180f + 15f, g.totalHeight, d)
    }
}
