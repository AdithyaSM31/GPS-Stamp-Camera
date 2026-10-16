package com.gpsstamp.camera.ultrawide

/** Plain width x height (android.util.Size can't be used in JVM unit tests). */
data class Size2(val width: Int, val height: Int)

/** What the app needs to know about one Camera2 camera, read from its characteristics. */
data class CameraSpec(
    val id: String,
    val facingBack: Boolean,
    val logical: Boolean,
    val backwardCompatible: Boolean,
    val focalLengths: List<Float>,
    val sensorWidthMm: Float,
    val sensorOrientation: Int,
    val jpegSizes: List<Size2>,
    val previewSizes: List<Size2>,
    val hasFlash: Boolean,
    val hasContinuousAf: Boolean,
)

/** An ultra-wide the phone hides from the camera list but lets the app open directly. */
data class HiddenUltraWide(
    val cameraId: String,
    val zoomRatio: Float,
    val sensorOrientation: Int,
    val jpegSize: Size2,
    val previewSize: Size2,
    val hasFlash: Boolean,
    val hasContinuousAf: Boolean,
)
