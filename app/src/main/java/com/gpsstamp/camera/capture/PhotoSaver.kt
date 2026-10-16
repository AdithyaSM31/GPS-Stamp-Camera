package com.gpsstamp.camera.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.gpsstamp.camera.stamp.StampData
import com.gpsstamp.camera.stamp.StampRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val ALBUM = "GPS Stamp Camera"
private const val JPEG_QUALITY = 92

/** Burns the stamp into a captured frame and saves it to the gallery with EXIF GPS and time. */
class PhotoSaver(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    /** Always closes [image]. */
    suspend fun save(image: ImageProxy, data: StampData): Uri = withContext(Dispatchers.IO) {
        val photo = image.use { it.toUprightBitmap() }
        try {
            val stamp = StampRenderer.render(data, photo.width)
            Canvas(photo).drawBitmap(stamp, 0f, (photo.height - stamp.height).toFloat(), null)
            stamp.recycle()

            val uri = insert(ExifFormat.fileName(data.timeMs, data.zone), data.timeMs)
            try {
                writeJpeg(uri, photo)
                writeExif(uri, data)
                publish(uri)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } finally {
            photo.recycle()
        }
    }

    /** Rotated to the true orientation and mutable. Front-camera frames are not mirrored, so text reads correctly. */
    private fun ImageProxy.toUprightBitmap(): Bitmap {
        val raw = toBitmap()
        val rotation = imageInfo.rotationDegrees
        val rotated = if (rotation == 0) raw else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { if (it !== raw) raw.recycle() }
        }
        if (rotated.isMutable) return rotated
        return rotated.copy(Bitmap.Config.ARGB_8888, true).also { rotated.recycle() }
    }

    private fun insert(fileName: String, timeMs: Long): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, timeMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
                if (!dir.exists() && !dir.mkdirs()) throw IOException("Can't create $dir")
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, File(dir, fileName).absolutePath)
            }
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Gallery refused the new photo")
    }

    private fun writeJpeg(uri: Uri, photo: Bitmap) {
        val out = resolver.openOutputStream(uri) ?: throw IOException("Can't open photo for writing")
        out.use { if (!photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) throw IOException("JPEG encoding failed") }
    }

    private fun writeExif(uri: Uri, data: StampData) {
        val pfd = resolver.openFileDescriptor(uri, "rw") ?: throw IOException("Can't open photo for EXIF")
        pfd.use {
            val dateTime = ExifFormat.dateTime(data.timeMs, data.zone)
            val offset = ExifFormat.offset(data.timeMs, data.zone)
            ExifInterface(it.fileDescriptor).apply {
                setLatLong(data.lat, data.lon)
                data.altitudeM?.let(::setAltitude)
                setAttribute(ExifInterface.TAG_DATETIME, dateTime)
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
                setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
                setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
                saveAttributes()
            }
        }
    }

    private fun publish(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    }
}
