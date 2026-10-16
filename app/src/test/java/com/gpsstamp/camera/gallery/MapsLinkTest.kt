package com.gpsstamp.camera.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class MapsLinkTest {
    @Test fun geoUri_dropsLabelledPin() = assertEquals(
        "geo:12.844842,80.152630?q=12.844842,80.152630(Photo%20location)",
        MapsLink.geoUri(12.844842, 80.152630)
    )

    @Test fun geoUri_negativeCoordinates() = assertEquals(
        "geo:-33.856784,-151.215297?q=-33.856784,-151.215297(Photo%20location)",
        MapsLink.geoUri(-33.856784, -151.215297)
    )

    @Test fun webUrl_isGoogleMapsSearch() = assertEquals(
        "https://www.google.com/maps/search/?api=1&query=12.844842,80.152630",
        MapsLink.webUrl(12.844842, 80.152630)
    )
}
