package com.gpsstamp.camera.stamp

import com.gpsstamp.camera.geo.ResolvedAddress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Stamp text: [title] is drawn larger, [body] lines follow in order. */
data class StampText(val title: String, val body: List<String>)

object StampFormatter {
    private val dateTimeFormat =
        DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy hh:mm a 'GMT' xxx", Locale.US)

    fun formatDateTime(timeMs: Long, zone: ZoneId): String =
        dateTimeFormat.format(Instant.ofEpochMilli(timeMs).atZone(zone))

    fun formatLatLong(lat: Double, lon: Double): String =
        String.format(Locale.US, "Lat %.6f° Long %.6f°", lat, lon)

    fun formatAccuracyAltitude(accuracyM: Float, altitudeM: Double?): String {
        val accuracy = "Accuracy ±${Math.round(accuracyM)} m"
        return if (altitudeM == null) accuracy else "$accuracy · Alt ${Math.round(altitudeM)} m"
    }

    /**
     * Without an address the coordinates become the title, so the separate
     * Lat/Long body line is dropped to avoid printing it twice.
     */
    fun buildText(
        lat: Double,
        lon: Double,
        accuracyM: Float,
        altitudeM: Double?,
        timeMs: Long,
        zone: ZoneId,
        address: ResolvedAddress?,
        note: String,
    ): StampText {
        val latLong = formatLatLong(lat, lon)
        val body = buildList {
            if (address != null) {
                add(address.fullLine)
                add(latLong)
            }
            add(formatAccuracyAltitude(accuracyM, altitudeM))
            add(formatDateTime(timeMs, zone))
            note.trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
        return StampText(title = address?.title ?: latLong, body = body)
    }
}
