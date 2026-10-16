package com.gpsstamp.camera.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocatedTest {
    private val place = Located(0.0, 0.0, "Keelakottiyur")

    @Test fun samePoint_returnsValue() = assertEquals("Keelakottiyur", place.valueNear(0.0, 0.0))

    @Test fun within25m_returnsValue() = assertEquals("Keelakottiyur", place.valueNear(0.0, 0.0002)) // ~22 m

    @Test fun beyond25m_returnsNull_soStaleAddressIsNeverStamped() = assertNull(place.valueNear(0.0, 0.0003)) // ~33 m
}
