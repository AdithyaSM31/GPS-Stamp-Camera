package com.gpsstamp.camera.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ExifFormatTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun dateTime_isLocalExifFormat() =
        assertEquals("2026:07:17 11:10:00", ExifFormat.dateTime(ms("2026-07-17T05:40:00Z"), ist))

    @Test fun offset_positive() = assertEquals("+05:30", ExifFormat.offset(ms("2026-07-17T05:40:00Z"), ist))

    @Test fun offset_negative() =
        assertEquals("-05:00", ExifFormat.offset(ms("2026-01-15T17:05:00Z"), ZoneId.of("America/New_York")))

    @Test fun offset_zero() = assertEquals("+00:00", ExifFormat.offset(ms("2026-07-17T05:40:00Z"), ZoneId.of("UTC")))

    @Test fun fileName_usesLocalTime() =
        assertEquals("GPS_20260717_111000.jpg", ExifFormat.fileName(ms("2026-07-17T05:40:00Z"), ist))
}
