package com.gpsstamp.camera.stamp

import android.graphics.Bitmap
import com.gpsstamp.camera.geo.ResolvedAddress
import java.time.ZoneId

/** Everything printed on one stamp, frozen at the moment of capture. */
data class StampData(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val altitudeM: Double?,
    val timeMs: Long,
    val zone: ZoneId,
    val address: ResolvedAddress?,
    val map: Bitmap?,
    val note: String,
)
