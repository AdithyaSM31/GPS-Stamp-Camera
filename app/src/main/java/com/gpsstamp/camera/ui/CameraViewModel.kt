package com.gpsstamp.camera.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gpsstamp.camera.BuildConfig
import com.gpsstamp.camera.R
import com.gpsstamp.camera.camera.CameraController
import com.gpsstamp.camera.capture.CaptureRules
import com.gpsstamp.camera.capture.CaptureStatus
import com.gpsstamp.camera.capture.PhotoSaver
import com.gpsstamp.camera.geo.AddressResolver
import com.gpsstamp.camera.geo.Located
import com.gpsstamp.camera.geo.ResolvedAddress
import com.gpsstamp.camera.location.LocationRepository
import com.gpsstamp.camera.location.LocationState
import com.gpsstamp.camera.map.OsmMapRenderer
import com.gpsstamp.camera.settings.SettingsStore
import com.gpsstamp.camera.stamp.StampData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

const val NOTE_MAX_LENGTH = 60
private const val TICK_MS = 1_000L
private const val LOOKUP_TIMEOUT_MS = 15_000L

data class CameraUiState(
    val status: CaptureStatus = CaptureStatus.Waiting(null),
    val capturing: Boolean = false,
    val preview: StampData? = null,
    val note: String = "",
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val zoom: Float = 1f,
    val lastPhotoUri: Uri? = null,
    val error: String? = null,
) {
    val canCapture get() = CaptureRules.canCapture(status, capturing)
}

/** Address and map looked up together for one point; either may be null when offline. */
private data class Place(val address: ResolvedAddress?, val map: Bitmap?)

class CameraViewModel(app: Application) : AndroidViewModel(app) {
    val camera = CameraController(app)
    private val locations = LocationRepository(app)
    private val addresses = AddressResolver(app)
    private val maps = OsmMapRenderer(app)
    private val saver = PhotoSaver(app)
    private val settings = SettingsStore(app)

    private val _ui = MutableStateFlow(CameraUiState())
    val ui: StateFlow<CameraUiState> = _ui.asStateFlow()

    private var location: LocationState = LocationState.Searching(null)
    // Only stamped when the current fix is within 25 m of where it was looked up, so a photo can never pair
    // one place's coordinates with another place's address or map.
    private var place: Located<Place>? = null
    private var enrichJob: Job? = null
    private var trackingJob: Job? = null

    init {
        viewModelScope.launch { _ui.update { it.copy(lensFacing = settings.lensFacing.first()) } }
        viewModelScope.launch {
            settings.note.collect { note ->
                _ui.update { it.copy(note = note) }
                refresh()
            }
        }
    }

    /** Starts GPS while the screen is visible; requires camera and location permission. */
    fun onStart() {
        if (trackingJob?.isActive == true) return
        trackingJob = viewModelScope.launch {
            launch {
                locations.states().collect { state ->
                    location = state
                    if (state is LocationState.Fix) enrich()
                    refresh()
                }
            }
            while (isActive) {
                refresh()
                delay(TICK_MS)
            }
        }
    }

    /** Stops GPS in the background; the next fix after returning must be fresh before the shutter enables. */
    fun onStop() {
        trackingJob?.cancel()
        trackingJob = null
        location = LocationState.Searching(null)
        refresh()
    }

    /**
     * Looks up address and map for the latest fix, one lookup at a time. If the user moved more than 25 m while
     * a lookup ran, it immediately looks up the newest fix instead of waiting for the next one.
     */
    private fun enrich() {
        if (enrichJob?.isActive == true) return
        enrichJob = viewModelScope.launch {
            var target = location as? LocationState.Fix ?: return@launch
            while (true) {
                val address = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { addresses.resolve(target.lat, target.lon) }
                val map = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { maps.render(target.lat, target.lon) }
                val looked = Located(target.lat, target.lon, Place(address, map))
                place = looked
                refresh()
                val latest = location as? LocationState.Fix ?: break
                if (looked.valueNear(latest.lat, latest.lon) != null) break
                target = latest
            }
        }
    }

    private fun refresh() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val status = CaptureRules.status(location, nowElapsed, BuildConfig.ALLOW_MOCK_LOCATION)
        val fix = location as? LocationState.Fix
        _ui.update { state ->
            val preview = fix?.let {
                val near = place?.valueNear(it.lat, it.lon)
                val now = it.timeAt(nowElapsed)
                // Minute precision is all the stamp shows; keeps the preview bitmap from re-rendering every tick.
                StampData(
                    it.lat, it.lon, it.accuracyM, it.altitudeM, now - now % 60_000, ZoneId.systemDefault(),
                    near?.address, near?.map, state.note,
                )
            }
            state.copy(status = status, preview = preview)
        }
    }

    fun capture() {
        val state = _ui.value
        if (!state.canCapture) return
        val fix = location as? LocationState.Fix ?: return
        val snapshot = state.preview?.copy(timeMs = fix.timeAt(SystemClock.elapsedRealtime())) ?: return
        _ui.update { it.copy(capturing = true) }
        viewModelScope.launch {
            try {
                val image = camera.takePicture()
                val uri = saver.save(image, snapshot)
                _ui.update { it.copy(lastPhotoUri = uri) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _ui.update { it.copy(error = getApplication<Application>().getString(R.string.save_failed, reason)) }
            } finally {
                _ui.update { it.copy(capturing = false) }
            }
        }
    }

    fun setNote(note: String) {
        viewModelScope.launch { settings.setNote(note.trim().take(NOTE_MAX_LENGTH)) }
    }

    fun toggleLens() {
        val next =
            if (_ui.value.lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
        _ui.update { it.copy(lensFacing = next, zoom = 1f) }
        viewModelScope.launch { settings.setLensFacing(next) }
    }

    fun cycleFlash() {
        val next = when (_ui.value.flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        camera.setFlash(next)
        _ui.update { it.copy(flashMode = next) }
    }

    fun setZoom(ratio: Float) {
        camera.setZoom(ratio)
        _ui.update { it.copy(zoom = ratio) }
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }
}
