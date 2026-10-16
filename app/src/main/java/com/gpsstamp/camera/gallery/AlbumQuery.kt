package com.gpsstamp.camera.gallery

/** The app's album in the shared Pictures folder, and the MediaStore filter that finds its photos. */
object AlbumQuery {
    const val ALBUM = "GPS Stamp Camera"

    /** API 29+ stores folders in relative_path; older versions only have the absolute file path in _data. */
    fun selection(sdkInt: Int): String =
        if (sdkInt >= 29) "relative_path LIKE 'Pictures/$ALBUM/%'"
        else "_data LIKE '%/Pictures/$ALBUM/%'"
}
