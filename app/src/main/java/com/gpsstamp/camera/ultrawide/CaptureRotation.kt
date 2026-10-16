package com.gpsstamp.camera.ultrawide

/** Clockwise degrees to rotate a back-camera frame so it is upright for how the phone is held (as CameraX does). */
object CaptureRotation {
    /** [surfaceRotation] is a Surface.ROTATION_* value (0..3). */
    fun degrees(sensorOrientation: Int, surfaceRotation: Int): Int =
        (sensorOrientation - surfaceRotation * 90 + 360) % 360
}
