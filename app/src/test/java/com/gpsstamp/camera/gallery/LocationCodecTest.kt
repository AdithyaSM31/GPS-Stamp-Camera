package com.gpsstamp.camera.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationCodecTest {
    @Test fun encode_sixDecimals() = assertEquals("12.844842,80.152630", LocationCodec.encode(12.844842, 80.15263))

    @Test fun decode_roundTrips() = assertEquals(12.844842 to 80.15263, LocationCodec.decode("12.844842,80.152630"))

    @Test fun decode_negative() = assertEquals(-33.856784 to -151.215297, LocationCodec.decode("-33.856784,-151.215297"))

    @Test fun decode_garbage_isNull() = assertNull(LocationCodec.decode("not a location"))

    @Test fun decode_missing_isNull() = assertNull(LocationCodec.decode(null))
}
