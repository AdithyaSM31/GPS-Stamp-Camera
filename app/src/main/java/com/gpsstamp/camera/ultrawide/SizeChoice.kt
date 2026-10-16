package com.gpsstamp.camera.ultrawide

import kotlin.math.abs

private const val FOUR_BY_THREE = 4f / 3f
private const val ASPECT_TOLERANCE = 0.02f

/** Output sizes are landscape (sensor orientation); 4:3 matches the photos the main camera takes. */
object SizeChoice {
    fun largest4by3(sizes: List<Size2>, maxWidth: Int, maxHeight: Int): Size2? =
        sizes
            .filter { it.width <= maxWidth && it.height <= maxHeight && it.height > 0 }
            .filter { abs(it.width.toFloat() / it.height - FOUR_BY_THREE) < ASPECT_TOLERANCE }
            .maxByOrNull { it.width.toLong() * it.height }
}
