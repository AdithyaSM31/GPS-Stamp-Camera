package com.gpsstamp.camera.capture

import com.gpsstamp.camera.location.LocationState

sealed interface CaptureStatus {
    data object Ready : CaptureStatus
    /** [accuracyM] is the current accuracy to show, or null when there is no recent fix. */
    data class Waiting(val accuracyM: Int?) : CaptureStatus
    data object MockDetected : CaptureStatus
    data object LocationOff : CaptureStatus
}

/** Decides when the shutter may fire, so no photo is stamped with a stale, vague or faked location. */
object CaptureRules {
    const val MAX_FIX_AGE_MS = 30_000L
    const val MAX_ACCURACY_M = 50f

    fun status(state: LocationState, nowElapsedMs: Long, allowMock: Boolean): CaptureStatus = when (state) {
        LocationState.Disabled -> CaptureStatus.LocationOff
        is LocationState.Searching -> CaptureStatus.Waiting(state.lastAccuracyM?.let(Math::round))
        is LocationState.Fix -> when {
            state.isMock && !allowMock -> CaptureStatus.MockDetected
            nowElapsedMs - state.elapsedRealtimeMs >= MAX_FIX_AGE_MS -> CaptureStatus.Waiting(null)
            state.accuracyM > MAX_ACCURACY_M -> CaptureStatus.Waiting(Math.round(state.accuracyM))
            else -> CaptureStatus.Ready
        }
    }

    fun canCapture(status: CaptureStatus, capturing: Boolean): Boolean =
        status == CaptureStatus.Ready && !capturing
}
