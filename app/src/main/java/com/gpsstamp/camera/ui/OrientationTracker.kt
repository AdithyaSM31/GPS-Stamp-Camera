package com.gpsstamp.camera.ui

import android.view.OrientationEventListener
import android.view.Surface
import kotlin.math.abs
import kotlin.math.min

private const val SNAP_WINDOW_DEG = 30

/** Turns OrientationEventListener degrees into a Surface rotation, with dead zones so it doesn't flicker near 45°. */
object OrientationTracker {
    // Device tilt (degrees clockwise) at the centre of each rotation; ROTATION_270 = phone turned clockwise.
    private val centres = mapOf(
        Surface.ROTATION_0 to 0,
        Surface.ROTATION_270 to 90,
        Surface.ROTATION_180 to 180,
        Surface.ROTATION_90 to 270,
    )

    /** Switches only when [degrees] is within 30° of a rotation's centre; otherwise (or lying flat) keeps [current]. */
    fun next(degrees: Int, current: Int): Int {
        if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) return current
        return centres.entries.firstOrNull { circularDistance(degrees, it.value) <= SNAP_WINDOW_DEG }?.key ?: current
    }

    private fun circularDistance(a: Int, b: Int): Int {
        val d = abs(a - b) % 360
        return min(d, 360 - d)
    }
}
