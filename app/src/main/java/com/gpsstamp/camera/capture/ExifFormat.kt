package com.gpsstamp.camera.capture

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** EXIF date strings and the photo file name, all in the capture's local time. */
object ExifFormat {
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val offsetFormat = DateTimeFormatter.ofPattern("xxx", Locale.US)
    private val fileNameFormat = DateTimeFormatter.ofPattern("'GPS_'yyyyMMdd_HHmmss'.jpg'", Locale.US)

    fun dateTime(timeMs: Long, zone: ZoneId): String = dateTimeFormat.format(Instant.ofEpochMilli(timeMs).atZone(zone))

    fun offset(timeMs: Long, zone: ZoneId): String = offsetFormat.format(Instant.ofEpochMilli(timeMs).atZone(zone))

    fun fileName(timeMs: Long, zone: ZoneId): String = fileNameFormat.format(Instant.ofEpochMilli(timeMs).atZone(zone))
}
