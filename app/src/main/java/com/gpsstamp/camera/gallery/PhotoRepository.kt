package com.gpsstamp.camera.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GalleryPhoto(val uri: Uri, val id: Long, val dateTakenMs: Long)

/**
 * The app's stamped photos in MediaStore. On Android 10+ this needs no storage permission: the app sees the
 * photos it created (kept across updates, not across uninstall).
 */
class PhotoRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Newest first. */
    suspend fun list(): List<GalleryPhoto> = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN)
        val sort = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC"
        val photos = mutableListOf<GalleryPhoto>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            AlbumQuery.selection(Build.VERSION.SDK_INT),
            null,
            sort,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                photos += GalleryPhoto(
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    id = id,
                    dateTakenMs = cursor.getLong(dateColumn),
                )
            }
        }
        photos
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.delete(uri, null, null) > 0
        } catch (e: SecurityException) {
            false
        }
    }

    /** GPS from the photo's EXIF; null without the photo-location permission (Android 10+) or without GPS tags. */
    suspend fun location(uri: Uri): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                if (!granted) return@withContext null
                MediaStore.setRequireOriginal(uri)
            } else {
                uri
            }
            resolver.openInputStream(source)?.use { stream ->
                ExifInterface(stream).latLong?.let { it[0] to it[1] }
            }
        } catch (e: Exception) {
            // SecurityException (permission revoked), IOException, unsupported file: no location button.
            null
        }
    }
}
