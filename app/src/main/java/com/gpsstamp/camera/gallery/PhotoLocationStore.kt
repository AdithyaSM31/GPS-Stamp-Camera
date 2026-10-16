package com.gpsstamp.camera.gallery

import android.content.Context
import java.util.Locale

/** "lat,lon" text form used to store a photo's coordinates. */
object LocationCodec {
    fun encode(lat: Double, lon: Double): String = String.format(Locale.US, "%.6f,%.6f", lat, lon)

    fun decode(text: String?): Pair<Double, Double>? {
        val parts = text?.split(',') ?: return null
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return lat to lon
    }
}

/**
 * Coordinates of each photo this app saved, keyed by MediaStore id, in app-private storage. Android hides EXIF
 * GPS from apps without a media-location permission it may never grant, so the app keeps its own copy.
 */
class PhotoLocationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("photo_locations", Context.MODE_PRIVATE)

    fun put(id: Long, lat: Double, lon: Double) {
        prefs.edit().putString(id.toString(), LocationCodec.encode(lat, lon)).apply()
    }

    fun get(id: Long): Pair<Double, Double>? = LocationCodec.decode(prefs.getString(id.toString(), null))

    fun remove(id: Long) {
        prefs.edit().remove(id.toString()).apply()
    }
}
