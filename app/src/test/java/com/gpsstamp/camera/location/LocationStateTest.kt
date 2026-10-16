package com.gpsstamp.camera.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationStateTest {
    @Test fun timeAt_advancesGnssTimeByElapsedRealtime_ignoringPhoneClock() {
        val fix = LocationState.Fix(
            lat = 12.843552, lon = 80.154484, accuracyM = 5f, altitudeM = null,
            elapsedRealtimeMs = 10_000L, timeMs = 1_784_266_800_000L, isMock = false,
        )
        assertEquals(1_784_266_802_500L, fix.timeAt(nowElapsedMs = 12_500L))
    }
}
