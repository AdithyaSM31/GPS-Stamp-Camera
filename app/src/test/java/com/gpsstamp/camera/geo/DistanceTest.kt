package com.gpsstamp.camera.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTest {
    @Test fun haversine_oneMilliDegreeAtEquator() =
        assertEquals(111.195, haversineMeters(0.0, 0.0, 0.0, 0.001), 0.01)

    @Test fun haversine_samePoint() =
        assertEquals(0.0, haversineMeters(12.84, 80.15, 12.84, 80.15), 1e-9)
}
