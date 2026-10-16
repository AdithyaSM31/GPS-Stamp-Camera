package com.gpsstamp.camera.ui

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRotationTest {
    @Test fun degreesFor_mapsEachRotationClockwise() {
        assertEquals(0f, OverlayRotation.degreesFor(Surface.ROTATION_0))
        assertEquals(90f, OverlayRotation.degreesFor(Surface.ROTATION_90))
        assertEquals(180f, OverlayRotation.degreesFor(Surface.ROTATION_180))
        assertEquals(-90f, OverlayRotation.degreesFor(Surface.ROTATION_270))
    }

    @Test fun isLandscape_onlyForSideways() {
        assertTrue(OverlayRotation.isLandscape(Surface.ROTATION_90))
        assertTrue(OverlayRotation.isLandscape(Surface.ROTATION_270))
        assertFalse(OverlayRotation.isLandscape(Surface.ROTATION_0))
        assertFalse(OverlayRotation.isLandscape(Surface.ROTATION_180))
    }

    @Test fun nearestEquivalent_turnsTheShortWay() {
        assertEquals(270f, OverlayRotation.nearestEquivalent(-90f, 180f))
        assertEquals(360f, OverlayRotation.nearestEquivalent(0f, 270f))
        assertEquals(90f, OverlayRotation.nearestEquivalent(90f, 0f))
    }
}
