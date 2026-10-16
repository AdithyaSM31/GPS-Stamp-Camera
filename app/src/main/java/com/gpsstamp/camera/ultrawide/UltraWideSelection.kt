package com.gpsstamp.camera.ultrawide

private const val MAX_WIDE_RATIO = 0.9f
// ~12.5 MP: the same memory as the main camera's 4032x3024 cap, and lets 4080x3072 sensors use full resolution.
private val JPEG_CAP = Size2(4096, 3072)
private val PREVIEW_CAP = Size2(1440, 1080)

/** Picks the widest usable hidden back camera that is genuinely wider than the main one. */
object UltraWideSelection {
    fun pick(main: CameraSpec, hidden: List<CameraSpec>): HiddenUltraWide? {
        val mainFocal = main.focalLengths.singleOrNull() ?: return null
        return hidden
            .asSequence()
            .filter { it.facingBack && !it.logical && it.backwardCompatible }
            .mapNotNull { candidate ->
                val focal = candidate.focalLengths.singleOrNull()?.takeIf { it < mainFocal } ?: return@mapNotNull null
                val ratio = UltraWideMath.equivalentZoom(mainFocal, main.sensorWidthMm, focal, candidate.sensorWidthMm)
                if (ratio > MAX_WIDE_RATIO) return@mapNotNull null
                val jpeg = SizeChoice.largest4by3(candidate.jpegSizes, JPEG_CAP.width, JPEG_CAP.height)
                    ?: return@mapNotNull null
                val preview = SizeChoice.largest4by3(candidate.previewSizes, PREVIEW_CAP.width, PREVIEW_CAP.height)
                    ?: return@mapNotNull null
                HiddenUltraWide(
                    cameraId = candidate.id,
                    zoomRatio = ratio,
                    sensorOrientation = candidate.sensorOrientation,
                    jpegSize = jpeg,
                    previewSize = preview,
                    hasFlash = candidate.hasFlash,
                    hasContinuousAf = candidate.hasContinuousAf,
                )
            }
            .minByOrNull { it.zoomRatio }
    }
}
