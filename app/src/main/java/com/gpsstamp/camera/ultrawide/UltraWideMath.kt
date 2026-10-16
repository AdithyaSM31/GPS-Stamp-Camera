package com.gpsstamp.camera.ultrawide

/** Zoom ratio a second lens is equivalent to, from horizontal field of view (tan(fov/2) = sensorWidth / 2f). */
object UltraWideMath {
    fun equivalentZoom(mainFocal: Float, mainSensorWidth: Float, wideFocal: Float, wideSensorWidth: Float): Float =
        (mainSensorWidth / mainFocal) / (wideSensorWidth / wideFocal)
}
