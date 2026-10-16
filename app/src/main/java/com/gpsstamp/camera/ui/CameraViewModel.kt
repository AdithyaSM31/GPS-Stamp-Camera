package com.gpsstamp.camera.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
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
import com.gpsstamp.camera.capture.rotatedUpright
import com.gpsstamp.camera.gallery.PhotoRepository
import com.gpsstamp.camera.geo.AddressResolver
import com.gpsstamp.camera.geo.Located
import com.gpsstamp.camera.geo.ResolvedAddress
import com.gpsstamp.camera.location.LocationRepository
import com.gpsstamp.camera.location.LocationState
import com.gpsstamp.camera.map.OsmMapRenderer
import com.gpsstamp.camera.settings.SettingsStore
import com.gpsstamp.camera.stamp.StampData
import com.gpsstamp.camera.ultrawide.CaptureRotation
import com.gpsstamp.camera.ultrawide.HiddenUltraWide
import com.gpsstamp.camera.ultrawide.UltraWideProbe
import com.gpsstamp.camera.ultrawide.UltraWideSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** An ultra-wide the phone hides from CameraX but lets the app open (null when there is none). */
    val hiddenWide: HiddenUltraWide? = null,
    /** True while the preview and shutter use [hiddenWide] instead of CameraX. */
    val ultraWideActive: Boolean = false,
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
    private val photos = PhotoRepository(app)

    private val _ui = MutableStateFlow(CameraUiState())
    val ui: StateFlow<CameraUiState> = _ui.asStateFlow()

    private var location: LocationState = LocationState.Searching(null)
    // Only stamped when the current fix is within 25 m of where it was looked up, so a photo can never pair
    // one place's coordinates with another place's address or map.
    private var place: Located<Place>? = null
    private var enrichJob: Job? = null
    private var trackingJob: Job? = null
    private val ultraWideProbe = UltraWideProbe(app)
    private var probed = false
    private var ultraSession: UltraWideSession? = null

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
        if (!probed) {
            probed = true
            viewModelScope.launch {
                val found = ultraWideProbe.find()
                _ui.update { it.copy(hiddenWide = found) }
            }
        }
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
        leaveUltraWide()
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
        val wide = state.hiddenWide.takeIf { state.ultraWideActive }
        // How the phone is held at the tap (as CameraX does), not after a slow flash capture.
        val degrees = wide?.let { CaptureRotation.degrees(it.sensorOrientation, camera.targetRotation) } ?: 0
        _ui.update { it.copy(capturing = true) }
        viewModelScope.launch {
            try {
                val uri = if (wide != null) {
                    val session = ultraSession ?: throw IllegalStateException("Ultra-wide camera isn't open")
                    val raw = session.capture()
                    saver.save(withContext(Dispatchers.Default) { raw.rotatedUpright(degrees) }, snapshot)
                } else {
                    saver.save(camera.takePicture(), snapshot)
                }
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

    /** Thumbnail shows the newest album photo, including ones from earlier sessions; null after deleting them all. */
    fun refreshLastPhoto() {
        viewModelScope.launch {
            val newest = photos.list().firstOrNull()?.uri
            _ui.update { it.copy(lastPhotoUri = newest) }
        }
    }

    fun setNote(note: String) {
        viewModelScope.launch { settings.setNote(note.trim().take(NOTE_MAX_LENGTH)) }
    }

    fun toggleLens() {
        leaveUltraWide()
        val next =
            if (_ui.value.lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
            else CameraSelector.LENS_FACING_BACK
        camera.setZoom(1f)
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
        ultraSession?.setFlash(next)
        _ui.update { it.copy(flashMode = next) }
    }

    /**
     * Zoom chip tapped. The hidden ultra-wide's ratio switches the preview to that lens (CameraX is released
     * and the screen hands over a surface); any other ratio returns to CameraX at that zoom.
     */
    fun selectZoom(ratio: Float) {
        val state = _ui.value
        val wide = state.hiddenWide
        if (wide != null && ratio == wide.zoomRatio && state.lensFacing == CameraSelector.LENS_FACING_BACK) {
            if (!state.ultraWideActive) {
                camera.unbind()
                _ui.update { it.copy(ultraWideActive = true, zoom = ratio) }
            }
            return
        }
        leaveUltraWide()
        camera.setZoom(ratio)
        _ui.update { it.copy(zoom = ratio) }
    }

    /** The ultra-wide preview surface appeared (open the lens) or went away (close it). */
    fun onUltraWideSurface(surface: Surface?) {
        ultraSession?.close()
        ultraSession = null
        val wide = _ui.value.hiddenWide
        if (surface == null || wide == null || !_ui.value.ultraWideActive) return
        var created: UltraWideSession? = null
        // Only the current session may fail ultra-wide mode; a late callback from an old one is ignored.
        val session = UltraWideSession(getApplication(), wide, onLost = {
            viewModelScope.launch { if (ultraSession === created) failUltraWide() }
        })
        created = session
        ultraSession = session
        session.setFlash(_ui.value.flashMode)
        viewModelScope.launch {
            try {
                camera.awaitReleased()
                if (ultraSession !== session) return@launch
                session.open(surface)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (ultraSession === session) failUltraWide()
            }
        }
    }

    private fun leaveUltraWide() {
        ultraSession?.close()
        ultraSession = null
        if (_ui.value.ultraWideActive) _ui.update { it.copy(ultraWideActive = false, zoom = 1f) }
    }

    /** The phone refused or took back the hidden lens: back to 1x and no wide chip for this session. */
    private fun failUltraWide() {
        ultraSession?.close()
        ultraSession = null
        camera.setZoom(1f)
        val message = getApplication<Application>().getString(R.string.ultra_wide_unavailable)
        _ui.update { it.copy(hiddenWide = null, ultraWideActive = false, zoom = 1f, error = message) }
    }

    override fun onCleared() {
        ultraSession?.close()
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }
}
