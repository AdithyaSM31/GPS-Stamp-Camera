package com.gpsstamp.camera.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoomOptionsTest {
    @Test fun wide_halfZoomPhone_offersHalf() = assertEquals(0.5f, ZoomOptions.wideRatio(0.5f))
    @Test fun wide_pixelStyle_offersPointSix() = assertEquals(0.6f, ZoomOptions.wideRatio(0.6f))
    @Test fun wide_noUltraWide_hidden() = assertNull(ZoomOptions.wideRatio(1f))
    @Test fun wide_barelyBelowOne_hidden() = assertNull(ZoomOptions.wideRatio(0.97f))

    @Test fun label_half() = assertEquals("0.5x", ZoomOptions.label(0.5f))
    @Test fun label_pointSix() = assertEquals("0.6x", ZoomOptions.label(0.6f))
    @Test fun label_roundsSensorNoise() = assertEquals("0.5x", ZoomOptions.label(0.49999f))
    @Test fun label_wholeNumbers() {
        assertEquals("1x", ZoomOptions.label(1f))
        assertEquals("2x", ZoomOptions.label(2f))
    }
}
