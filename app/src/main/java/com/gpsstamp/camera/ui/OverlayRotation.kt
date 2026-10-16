package com.gpsstamp.camera.ui

import android.view.Surface
import kotlin.math.roundToInt

/** How far to rotate on-screen content (clockwise) so it reads upright for a given device rotation. */
object OverlayRotation {
    fun degreesFor(surfaceRotation: Int): Float = when (surfaceRotation) {
        Surface.ROTATION_90 -> 90f
        Surface.ROTATION_180 -> 180f
        Surface.ROTATION_270 -> -90f
        else -> 0f
    }

    fun isLandscape(surfaceRotation: Int): Boolean =
        surfaceRotation == Surface.ROTATION_90 || surfaceRotation == Surface.ROTATION_270

    /** [target] shifted by whole turns to be closest to [current], so animations take the short way round. */
    fun nearestEquivalent(target: Float, current: Float): Float =
        target + 360f * ((current - target) / 360f).roundToInt()
}
