package com.gpsstamp.camera.ui

import java.util.Locale

// Below this the camera has a real ultra-wide lens; just under 1x is only sensor-crop noise.
private const val MAX_WIDE_RATIO = 0.9f

/** Zoom buttons: the wide one matches the phone's actual ultra-wide (0.5x, 0.6x, ...), not a fixed value. */
object ZoomOptions {
    /** The phone's minimum zoom as the wide option, or null when it has no ultra-wide lens. */
    fun wideRatio(minZoom: Float): Float? = minZoom.takeIf { it <= MAX_WIDE_RATIO }

    /** "0.5x", "0.6x", "1x", "2x": one decimal, trailing ".0" dropped. */
    fun label(ratio: Float): String {
        val text = String.format(Locale.US, "%.1f", ratio).removeSuffix(".0")
        return "${text}x"
    }
}
