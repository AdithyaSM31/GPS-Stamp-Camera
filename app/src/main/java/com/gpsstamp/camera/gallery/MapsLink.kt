package com.gpsstamp.camera.gallery

import java.util.Locale

private const val PIN_LABEL = "Photo%20location"

/** Links that open a photo's location in a maps app, or in the browser when no maps app is installed. */
object MapsLink {
    fun geoUri(lat: Double, lon: Double): String {
        val point = point(lat, lon)
        return "geo:$point?q=$point($PIN_LABEL)"
    }

    fun webUrl(lat: Double, lon: Double): String =
        "https://www.google.com/maps/search/?api=1&query=${point(lat, lon)}"

    private fun point(lat: Double, lon: Double) = String.format(Locale.US, "%.6f,%.6f", lat, lon)
}
