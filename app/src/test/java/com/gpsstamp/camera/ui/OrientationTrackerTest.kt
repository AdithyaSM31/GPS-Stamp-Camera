package com.gpsstamp.camera.ui

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationTrackerTest {
    private val r0 = Surface.ROTATION_0
    private val r90 = Surface.ROTATION_90
    private val r180 = Surface.ROTATION_180
    private val r270 = Surface.ROTATION_270

    @Test fun upright_staysPortrait() = assertEquals(r0, OrientationTracker.next(0, r0))
    @Test fun tiltedRightPast60_switchesToLandscape() = assertEquals(r270, OrientationTracker.next(80, r0))
    @Test fun deadZone_keepsPortrait() = assertEquals(r0, OrientationTracker.next(50, r0))
    @Test fun deadZone_keepsLandscape() = assertEquals(r270, OrientationTracker.next(50, r270))
    @Test fun nearZeroAcrossWrap_isPortrait() = assertEquals(r0, OrientationTracker.next(350, r90))
    @Test fun tiltedLeft_switchesToOtherLandscape() = assertEquals(r90, OrientationTracker.next(265, r0))
    @Test fun upsideDown_isRotation180() = assertEquals(r180, OrientationTracker.next(185, r0))
    @Test fun flatPhone_keepsCurrent() = assertEquals(r180, OrientationTracker.next(-1, r180))
}
