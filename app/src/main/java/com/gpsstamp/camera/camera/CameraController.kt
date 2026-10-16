package com.gpsstamp.camera.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** CameraX preview + still capture with lens, zoom and flash control. 4:3 throughout so preview matches the photo. */
class CameraController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val providerFuture = ProcessCameraProvider.getInstance(appContext)

    private val preview = Preview.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
        )
        .build()

    // Capped near 12 MP: a 50-200 MP frame decoded to an ARGB bitmap would run the app out of memory.
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(4032, 3024), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()
        )
        .build()

    private var camera: Camera? = null

    private val _zoomRange = MutableStateFlow(1f..1f)
    val zoomRange: StateFlow<ClosedFloatingPointRange<Float>> = _zoomRange.asStateFlow()

    /** True only when both a front and a back camera exist. */
    private val _canSwitchLens = MutableStateFlow(false)
    val canSwitchLens: StateFlow<Boolean> = _canSwitchLens.asStateFlow()

    /** Binds the requested lens, falling back to whichever camera exists (some tablets have no back camera). */
    fun bind(owner: LifecycleOwner, previewView: PreviewView, lensFacing: Int) {
        providerFuture.addListener({
            val provider = providerFuture.get()
            val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            _canSwitchLens.value = hasFront && hasBack
            val selector = when {
                lensFacing == CameraSelector.LENS_FACING_FRONT && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> return@addListener // No camera: capture will fail with an error message instead of crashing.
            }

            camera?.cameraInfo?.zoomState?.removeObservers(owner)
            provider.unbindAll()
            preview.surfaceProvider = previewView.surfaceProvider
            camera = try {
                provider.bindToLifecycle(owner, selector, preview, imageCapture).also { bound ->
                    bound.cameraInfo.zoomState.observe(owner) { _zoomRange.value = it.minZoomRatio..it.maxZoomRatio }
                }
            } catch (e: IllegalArgumentException) {
                null
            }
        }, mainExecutor)
    }

    fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun setFlash(mode: Int) {
        imageCapture.flashMode = mode
    }

    /** Surface.ROTATION_* of the device, so a landscape shot is saved landscape while the UI stays portrait. */
    fun setTargetRotation(rotation: Int) {
        imageCapture.targetRotation = rotation
    }

    suspend fun takePicture(): ImageProxy = suspendCancellableCoroutine { cont ->
        imageCapture.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                if (cont.isActive) cont.resume(image) else image.close()
            }

            override fun onError(exception: ImageCaptureException) {
                cont.resumeWithException(exception)
            }
        })
    }
}
