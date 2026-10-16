package com.gpsstamp.camera.ultrawide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UltraWideMathTest {
    @Test fun nothingPhone1_ultraWideIsPointSix() =
        assertEquals(0.603f, UltraWideMath.equivalentZoom(5.59f, 8.192f, 2.15f, 5.2224f), 0.005f)

    @Test fun sameLens_isOne() = assertEquals(1f, UltraWideMath.equivalentZoom(4f, 6f, 4f, 6f), 1e-6f)
}

class SizeChoiceTest {
    private val sizes = listOf(Size2(4080, 3072), Size2(4000, 3000), Size2(1920, 1080), Size2(1440, 1080), Size2(640, 480))

    @Test fun jpegCap_picksLargest4by3Within() = assertEquals(Size2(4000, 3000), SizeChoice.largest4by3(sizes, 4032, 3024))
    @Test fun previewCap_picksLargest4by3Within() = assertEquals(Size2(1440, 1080), SizeChoice.largest4by3(sizes, 1440, 1080))
    @Test fun no4by3_isNull() = assertNull(SizeChoice.largest4by3(listOf(Size2(1920, 1080)), 4032, 3024))
}

class CaptureRotationTest {
    @Test fun sensor90_eachDeviceRotation() {
        assertEquals(90, CaptureRotation.degrees(90, 0))
        assertEquals(0, CaptureRotation.degrees(90, 1))
        assertEquals(270, CaptureRotation.degrees(90, 2))
        assertEquals(180, CaptureRotation.degrees(90, 3))
    }
}

class UltraWideSelectionTest {
    private val wideSizes = listOf(Size2(4080, 3072), Size2(4000, 3000), Size2(1920, 1080), Size2(1440, 1080))

    private fun spec(
        id: String,
        focal: Float,
        sensorW: Float,
        back: Boolean = true,
        logical: Boolean = false,
        jpeg: List<Size2> = wideSizes,
    ) = CameraSpec(
        id = id, facingBack = back, logical = logical, backwardCompatible = true, focalLengths = listOf(focal),
        sensorWidthMm = sensorW, sensorOrientation = 90, jpegSizes = jpeg, previewSizes = wideSizes,
        hasFlash = true, hasContinuousAf = true,
    )

    private val main = spec("0", 5.59f, 8.192f)
    private val uw = spec("2", 2.15f, 5.2224f)

    @Test fun nothingPhone1_picksCameraTwo() {
        val pick = UltraWideSelection.pick(main, listOf(uw))!!
        assertEquals("2", pick.cameraId)
        assertEquals(0.603f, pick.zoomRatio, 0.005f)
        assertEquals(Size2(4000, 3000), pick.jpegSize)
        assertEquals(Size2(1440, 1080), pick.previewSize)
    }

    @Test fun frontCamera_skipped() = assertNull(UltraWideSelection.pick(main, listOf(spec("2", 2.15f, 5.2224f, back = false))))
    @Test fun logicalCamera_skipped() = assertNull(UltraWideSelection.pick(main, listOf(spec("3", 2.15f, 5.2224f, logical = true))))
    @Test fun longerLens_skipped() = assertNull(UltraWideSelection.pick(main, listOf(spec("2", 7f, 8.192f))))
    @Test fun no4by3Jpeg_skipped() =
        assertNull(UltraWideSelection.pick(main, listOf(spec("2", 2.15f, 5.2224f, jpeg = listOf(Size2(1920, 1080))))))
    @Test fun barelyWider_skipped() = assertNull(UltraWideSelection.pick(main, listOf(spec("2", 5.3f, 8.192f)))) // 5.3/5.59 = 0.948

    @Test fun twoCandidates_widestWins() {
        val pointSix = spec("4", 2.15f, 5.2224f)
        val pointSevenFive = spec("5", 4.1925f, 8.192f) // 5.59 * 0.75
        assertEquals("4", UltraWideSelection.pick(main, listOf(pointSevenFive, pointSix))!!.cameraId)
    }
}
