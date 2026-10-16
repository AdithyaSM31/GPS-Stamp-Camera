package com.gpsstamp.camera.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

private const val REFRESH_DISTANCE_M = 25.0

/** Turns coordinates into a stamp address with the platform Geocoder; null when offline or unavailable. */
class AddressResolver(context: Context) {
    private val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
    private val mutex = Mutex()
    private var cachedLat = 0.0
    private var cachedLon = 0.0
    private var cached: ResolvedAddress? = null

    suspend fun resolve(lat: Double, lon: Double): ResolvedAddress? = mutex.withLock {
        cached?.let { if (haversineMeters(cachedLat, cachedLon, lat, lon) <= REFRESH_DISTANCE_M) return it }
        if (!Geocoder.isPresent()) return null
        val resolved = lookup(lat, lon)?.toResolved() ?: return null
        cachedLat = lat
        cachedLon = lon
        cached = resolved
        resolved
    }

    private suspend fun lookup(lat: Double, lon: Double): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = cont.resume(null)
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                } catch (e: IOException) {
                    null
                }
            }
        }

    private fun Address.toResolved(): ResolvedAddress? {
        val title = listOfNotNull(locality ?: subAdminArea, adminArea, countryName)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        val fullLine = getAddressLine(0)?.takeIf { it.isNotBlank() }
        if (title.isEmpty() && fullLine == null) return null
        return ResolvedAddress(title = title.ifEmpty { fullLine!! }, fullLine = fullLine ?: title)
    }
}
