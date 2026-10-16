package com.gpsstamp.camera.ultrawide

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.camera.core.ImageCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val PRECAPTURE_TIMEOUT_MS = 1_500L
private const val TRIGGER_TAG = "precapture-trigger"

/** The hidden ultra-wide could not be opened, was lost (another app took it, the phone refused, ...) or closed. */
class UltraWideUnavailable(message: String) : Exception(message)

/**
 * Plain Camera2 session for a camera CameraX can't see: preview into [open]'s surface and JPEG stills. One
 * instance per open; [close] is final and fails anything still waiting. [onLost] fires if the camera goes away
 * after opening.
 */
class UltraWideSession(context: Context, private val spec: HiddenUltraWide, private val onLost: () -> Unit) {
    private val manager = context.applicationContext.getSystemService(CameraManager::class.java)
    private val thread = HandlerThread("ultrawide").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val calls = PendingCalls()

    @Volatile private var device: CameraDevice? = null
    @Volatile private var session: CameraCaptureSession? = null
    @Volatile private var reader: ImageReader? = null
    @Volatile private var preview: Surface? = null
    @Volatile private var flashMode = ImageCapture.FLASH_MODE_OFF

    @SuppressLint("MissingPermission") // Only reachable from the camera screen, after the permission gate.
    suspend fun open(previewSurface: Surface) {
        val camera = calls.await { cont ->
            manager.openCamera(spec.cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (cont.isActive) cont.resume(camera) else camera.close()
                }

                override fun onDisconnected(camera: CameraDevice) = lost(camera, "disconnected")
                override fun onError(camera: CameraDevice, error: Int) = lost(camera, "error $error")

                private fun lost(camera: CameraDevice, why: String) {
                    camera.close()
                    if (cont.isActive) cont.resumeWithException(UltraWideUnavailable(why))
                    else if (!calls.isClosed) onLost()
                }
            }, handler)
        }
        // close() may have run while this coroutine waited to resume; it couldn't see this camera yet.
        device = camera
        if (calls.isClosed) {
            camera.close()
            throw UltraWideUnavailable("Ultra-wide camera closed")
        }
        preview = previewSurface
        val imageReader = ImageReader.newInstance(spec.jpegSize.width, spec.jpegSize.height, ImageFormat.JPEG, 2)
        reader = imageReader

        session = calls.await { cont ->
            val callback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (cont.isActive) cont.resume(s) else s.close()
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    if (cont.isActive) cont.resumeWithException(UltraWideUnavailable("session configuration failed"))
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val outputs = listOf(OutputConfiguration(previewSurface), OutputConfiguration(imageReader.surface))
                camera.createCaptureSession(
                    SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, callback)
                )
            } else {
                @Suppress("DEPRECATION")
                camera.createCaptureSession(listOf(previewSurface, imageReader.surface), callback, handler)
            }
        }
        startPreview()
    }

    fun setFlash(mode: Int) {
        flashMode = mode
        handler.post { runCatching { startPreview() } }
    }

    /** Decoded JPEG, mutable, in sensor orientation (not rotated). Fails if the session closes meanwhile. */
    suspend fun capture(): Bitmap {
        val s = session ?: throw IllegalStateException("Ultra-wide camera isn't open")
        val r = reader ?: throw IllegalStateException("Ultra-wide camera isn't open")
        if (spec.hasFlash && flashMode != ImageCapture.FLASH_MODE_OFF) precapture(s)

        val bytes = calls.await { cont ->
            r.setOnImageAvailableListener({ source ->
                try {
                    val image = source.acquireNextImage() ?: return@setOnImageAvailableListener
                    try {
                        val buffer = image.planes[0].buffer
                        val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        if (cont.isActive) cont.resume(data)
                    } finally {
                        image.close()
                    }
                } catch (e: IllegalStateException) {
                    // The reader was closed under us; close() has failed (or will fail) the waiter.
                    if (cont.isActive) cont.resumeWithException(UltraWideUnavailable("Ultra-wide camera closed"))
                }
            }, handler)
            val request = requestBuilder(CameraDevice.TEMPLATE_STILL_CAPTURE, r.surface).build()
            s.capture(request, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("Ultra-wide capture failed"))
                }
            }, handler)
        }
        return withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inMutable = true })
                ?: throw IOException("Couldn't decode the ultra-wide photo")
        }
    }

    /**
     * Final and idempotent. Fails any waiting open/capture first, disconnects the camera right away (so CameraX
     * can reopen camera 0 straight after), and closes the reader on the camera thread so it can't race the
     * image listener.
     */
    fun close() {
        if (calls.isClosed) return
        calls.close()
        runCatching { session?.close() }
        runCatching { device?.close() }
        val imageReader = reader
        session = null
        device = null
        reader = null
        handler.post {
            runCatching { imageReader?.close() }
            thread.quitSafely()
        }
    }

    private fun startPreview() {
        val s = session ?: return
        val surface = preview ?: return
        s.setRepeatingRequest(requestBuilder(CameraDevice.TEMPLATE_PREVIEW, surface).build(), null, handler)
    }

    private fun requestBuilder(template: Int, target: Surface): CaptureRequest.Builder =
        (device ?: throw IllegalStateException("Ultra-wide camera isn't open")).createCaptureRequest(template).apply {
            addTarget(target)
            set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            set(
                CaptureRequest.CONTROL_AF_MODE,
                if (spec.hasContinuousAf) CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE else CameraMetadata.CONTROL_AF_MODE_OFF
            )
            set(CaptureRequest.CONTROL_AE_MODE, aeMode())
        }

    private fun aeMode(): Int = if (!spec.hasFlash) CameraMetadata.CONTROL_AE_MODE_ON else when (flashMode) {
        ImageCapture.FLASH_MODE_AUTO -> CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
        ImageCapture.FLASH_MODE_ON -> CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
        else -> CameraMetadata.CONTROL_AE_MODE_ON
    }

    /** Lets auto-exposure meter for the flash; gives up after 1.5 s and shoots anyway. */
    private suspend fun precapture(s: CameraCaptureSession) {
        val surface = preview ?: return
        withTimeoutOrNull(PRECAPTURE_TIMEOUT_MS) {
            calls.await<Unit> { cont ->
                var triggered = false
                val watcher = object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        if (request.tag == TRIGGER_TAG) {
                            triggered = true
                            return
                        }
                        val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                        val settled = ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                            ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED || ae == CaptureResult.CONTROL_AE_STATE_LOCKED
                        if (triggered && settled && cont.isActive) cont.resume(Unit)
                    }
                }
                s.setRepeatingRequest(requestBuilder(CameraDevice.TEMPLATE_PREVIEW, surface).build(), watcher, handler)
                val trigger = requestBuilder(CameraDevice.TEMPLATE_PREVIEW, surface).apply {
                    set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                    setTag(TRIGGER_TAG)
                }
                s.capture(trigger.build(), watcher, handler)
            }
        }
        if (!calls.isClosed) startPreview()
    }
}
