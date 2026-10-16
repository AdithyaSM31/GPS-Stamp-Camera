package com.gpsstamp.camera.location

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val UPDATE_INTERVAL_MS = 2_000L

/** High-accuracy fused location as a stream of [LocationState]. Collect only after location permission is granted. */
class LocationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    fun states(): Flow<LocationState> = callbackFlow {
        fun providerState() =
            if (LocationManagerCompat.isLocationEnabled(locationManager)) LocationState.Searching(null)
            else LocationState.Disabled

        trySend(providerState())

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toFix()) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS).build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(providerState())
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        awaitClose {
            client.removeLocationUpdates(callback)
            appContext.unregisterReceiver(receiver)
        }
    }

    private fun Location.toFix() = LocationState.Fix(
        lat = latitude,
        lon = longitude,
        accuracyM = accuracy,
        altitudeM = if (hasAltitude()) altitude else null,
        elapsedRealtimeMs = elapsedRealtimeNanos / 1_000_000,
        isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else @Suppress("DEPRECATION") isFromMockProvider,
    )
}
