package com.gpsstamp.camera.capture

import com.gpsstamp.camera.location.LocationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRulesTest {
    private val now = 100_000L

    private fun fix(acc: Float = 5f, ageMs: Long = 1_000L, mock: Boolean = false) =
        LocationState.Fix(
            lat = 12.843552, lon = 80.154484, accuracyM = acc, altitudeM = 42.0,
            elapsedRealtimeMs = now - ageMs, timeMs = 0L, isMock = mock,
        )

    private fun status(state: LocationState, allowMock: Boolean = false) = CaptureRules.status(state, now, allowMock)

    @Test fun freshAccurateFix_isReady() = assertEquals(CaptureStatus.Ready, status(fix()))
    @Test fun accuracyAtLimit_isReady() = assertEquals(CaptureStatus.Ready, status(fix(acc = 50f)))
    @Test fun accuracyOverLimit_waitsWithRoundedAccuracy() = assertEquals(CaptureStatus.Waiting(50), status(fix(acc = 50.4f)))
    @Test fun ageJustUnderLimit_isReady() = assertEquals(CaptureStatus.Ready, status(fix(ageMs = 29_999)))
    @Test fun ageAtLimit_waitsWithoutAccuracy() = assertEquals(CaptureStatus.Waiting(null), status(fix(ageMs = 30_000)))
    @Test fun mockFix_isBlocked() = assertEquals(CaptureStatus.MockDetected, status(fix(mock = true)))
    @Test fun mockFix_allowedByDebugFlag() = assertEquals(CaptureStatus.Ready, status(fix(mock = true), allowMock = true))
    @Test fun mockWinsOverWeakAccuracy() = assertEquals(CaptureStatus.MockDetected, status(fix(acc = 120f, mock = true)))
    @Test fun searching_reportsRoundedAccuracy() = assertEquals(CaptureStatus.Waiting(119), status(LocationState.Searching(118.6f)))
    @Test fun searching_noAccuracyYet() = assertEquals(CaptureStatus.Waiting(null), status(LocationState.Searching(null)))
    @Test fun disabled_isLocationOff() = assertEquals(CaptureStatus.LocationOff, status(LocationState.Disabled))

    @Test fun canCapture_whenReadyAndIdle() = assertTrue(CaptureRules.canCapture(CaptureStatus.Ready, capturing = false))
    @Test fun cannotCapture_whileCapturing() = assertFalse(CaptureRules.canCapture(CaptureStatus.Ready, capturing = true))
    @Test fun cannotCapture_whileWaiting() = assertFalse(CaptureRules.canCapture(CaptureStatus.Waiting(5), capturing = false))
}
