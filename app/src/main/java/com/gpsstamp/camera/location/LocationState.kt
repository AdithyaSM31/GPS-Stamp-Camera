package com.gpsstamp.camera.location

sealed interface LocationState {
    /** No usable fix yet; [lastAccuracyM] is the best accuracy seen so far, if any. */
    data class Searching(val lastAccuracyM: Float?) : LocationState

    /** [elapsedRealtimeMs] is on the SystemClock.elapsedRealtime() timeline, so fix age ignores wall-clock changes. */
    data class Fix(
        val lat: Double,
        val lon: Double,
        val accuracyM: Float,
        val altitudeM: Double?,
        val elapsedRealtimeMs: Long,
        val isMock: Boolean,
    ) : LocationState

    data object Disabled : LocationState
}
