package com.gpsstamp.camera.ultrawide

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HIDDEN_ID_RANGE = 0..9

/**
 * Some phones (e.g. Nothing Phone (1)) leave their ultra-wide out of the camera list given to apps but still
 * let apps open it by id. Finds such a camera without opening it; null when there is none.
 */
class UltraWideProbe(context: Context) {
    private val manager = context.applicationContext.getSystemService(CameraManager::class.java)

    suspend fun find(): HiddenUltraWide? = withContext(Dispatchers.IO) {
        try {
            val listed = manager.cameraIdList.toSet()
            val main = listed.asSequence()
                .mapNotNull { read(it) }
                .firstOrNull { it.facingBack && !it.logical }
                ?: return@withContext null
            val hidden = HIDDEN_ID_RANGE.map { it.toString() }.filterNot { it in listed }.mapNotNull { read(it) }
            UltraWideSelection.pick(main, hidden)
        } catch (e: Exception) {
            null
        }
    }

    /** Characteristics as plain data; null when the id doesn't exist or isn't readable by this app. */
    private fun read(id: String): CameraSpec? = try {
        val c = manager.getCameraCharacteristics(id)
        val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        CameraSpec(
            id = id,
            facingBack = c.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK,
            logical = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in capabilities,
            backwardCompatible = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE in capabilities,
            focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
            sensorWidthMm = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.width ?: 0f,
            sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
            jpegSizes = map?.getOutputSizes(ImageFormat.JPEG).orEmpty().map { Size2(it.width, it.height) },
            previewSizes = map?.getOutputSizes(SurfaceTexture::class.java).orEmpty().map { Size2(it.width, it.height) },
            hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            hasContinuousAf = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                ?.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true,
        )
    } catch (e: Exception) {
        null
    }
}
