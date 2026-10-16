package com.gpsstamp.camera.stamp

import com.gpsstamp.camera.geo.ResolvedAddress
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StampFormatterTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val ny = ZoneId.of("America/New_York")
    private val utc = ZoneId.of("UTC")
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun dateTime_positiveOffset() =
        assertEquals("Friday, 17/07/2026 11:10 AM GMT +05:30", StampFormatter.formatDateTime(ms("2026-07-17T05:40:00Z"), ist))

    @Test fun dateTime_negativeOffset() =
        assertEquals("Thursday, 15/01/2026 12:05 PM GMT -05:00", StampFormatter.formatDateTime(ms("2026-01-15T17:05:00Z"), ny))

    @Test fun dateTime_zeroOffset() =
        assertEquals("Friday, 17/07/2026 12:00 AM GMT +00:00", StampFormatter.formatDateTime(ms("2026-07-17T00:00:00Z"), utc))

    @Test fun latLong() =
        assertEquals("Lat 12.843552° Long 80.154484°", StampFormatter.formatLatLong(12.843552, 80.154484))

    @Test fun latLong_negative() =
        assertEquals("Lat -33.856784° Long -151.215297°", StampFormatter.formatLatLong(-33.856784, -151.215297))

    @Test fun accuracyAltitude_rounds() =
        assertEquals("Accuracy ±5 m · Alt 42 m", StampFormatter.formatAccuracyAltitude(4.6f, 41.5))

    @Test fun accuracy_noAltitude() =
        assertEquals("Accuracy ±12 m", StampFormatter.formatAccuracyAltitude(12.2f, null))

    @Test fun buildText_withAddressAndNote() {
        val t = StampFormatter.buildText(
            12.843552, 80.154484, 4.6f, 41.5, ms("2026-07-17T05:40:00Z"), ist,
            ResolvedAddress(
                "Keelakottiyur, Tamil Nadu, India",
                "R5V3+9GJ Academic Block, VIT University, Keelakottiyur, Tamil Nadu 600127, India"
            ),
            "Project review – Team 4"
        )
        assertEquals("Keelakottiyur, Tamil Nadu, India", t.title)
        assertEquals(
            listOf(
                "R5V3+9GJ Academic Block, VIT University, Keelakottiyur, Tamil Nadu 600127, India",
                "Lat 12.843552° Long 80.154484°",
                "Accuracy ±5 m · Alt 42 m",
                "Friday, 17/07/2026 11:10 AM GMT +05:30",
                "Project review – Team 4"
            ),
            t.body
        )
    }

    @Test fun buildText_noAddress_titleIsCoordinates_noDuplicateLine_blankNoteOmitted() {
        val t = StampFormatter.buildText(12.843552, 80.154484, 4.6f, null, ms("2026-07-17T05:40:00Z"), ist, null, "   ")
        assertEquals("Lat 12.843552° Long 80.154484°", t.title)
        assertEquals(listOf("Accuracy ±5 m", "Friday, 17/07/2026 11:10 AM GMT +05:30"), t.body)
    }
}
