package com.gpsstamp.camera.stamp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StampGeometryTest {
    private val d = 0.01f

    @Test fun withMap_mapIsSquareAndPanelIsAtLeastMapHeight() {
        val g = StampGeometryCalc.compute(1000, hasMap = true, bodyLineCount = 4)
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
        val g = StampGeometryCalc.compute(1000, hasMap = false, bodyLineCount = 6)
        assertNull(g.map)
        assertEquals(20f, g.panel.left, d)
        assertEquals(44f + 50f + 210f, g.panel.bottom - g.panel.top, d)
    }

    @Test fun scalesLinearlyWithWidth() = assertEquals(
        2 * StampGeometryCalc.compute(1000, true, 4).totalHeight,
        StampGeometryCalc.compute(2000, true, 4).totalHeight,
        d
    )

    @Test fun textWidth_isPanelWidthMinusPadding() =
        assertEquals(980f - 280f - 2 * 22f, StampGeometryCalc.textWidth(1000, hasMap = true), d)
}
