package com.gpsstamp.camera.geo

/** How far a fix may be from where an address or map was looked up and still be stamped with it. */
const val PLACE_MATCH_DISTANCE_M = 25.0

/** A value (address, map) tied to the point it was computed for. */
data class Located<T>(val lat: Double, val lon: Double, val value: T) {
    /** [value] if ([lat], [lon]) is within [PLACE_MATCH_DISTANCE_M], else null — never another place's data. */
    fun valueNear(lat: Double, lon: Double): T? =
        if (haversineMeters(this.lat, this.lon, lat, lon) <= PLACE_MATCH_DISTANCE_M) value else null
}
