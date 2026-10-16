package com.gpsstamp.camera.location

sealed interface LocationState {
    /** No usable fix yet; [lastAccuracyM] is the best accuracy seen so far, if any. */
    data class Searching(val lastAccuracyM: Float?) : LocationState

    /**
     * [elapsedRealtimeMs] is on the SystemClock.elapsedRealtime() timeline, so fix age ignores wall-clock changes.
     * [timeMs] is the fix's own UTC time (GNSS-derived for GPS fixes), independent of the phone's clock setting.
     */
    data class Fix(
        val lat: Double,
        val lon: Double,
        val accuracyM: Float,
        val altitudeM: Double?,
        val elapsedRealtimeMs: Long,
        val timeMs: Long,
        val isMock: Boolean,
    ) : LocationState {
        /** UTC time now, carried forward from the fix by elapsed realtime, so changing the phone's clock can't fake it. */
        fun timeAt(nowElapsedMs: Long): Long = timeMs + (nowElapsedMs - elapsedRealtimeMs)
    }

    data object Disabled : LocationState
}
