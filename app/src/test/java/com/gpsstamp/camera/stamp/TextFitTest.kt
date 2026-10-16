package com.gpsstamp.camera.stamp

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFitTest {
    /** Every character is 10 px wide. */
    private val m: (String) -> Float = { it.length * 10f }

    @Test fun ellipsize_fits_unchanged() = assertEquals("Hi", TextFit.ellipsize("Hi", 60f, m))

    @Test fun ellipsize_tooLong_trimsAndAddsEllipsis() = assertEquals("Hello…", TextFit.ellipsize("Hello world", 60f, m))

    @Test fun wrap_overflow_ellipsizesLastLine() = assertEquals(
        listOf("576 Chattanooga", "Valley Rd Flintston…"),
        TextFit.wrap("576 Chattanooga Valley Rd Flintstone GA", 200f, 2, m)
    )

    @Test fun wrap_fits_singleLine() = assertEquals(listOf("Short line"), TextFit.wrap("Short line", 200f, 2, m))

    @Test fun wrap_singleOverlongWord_ellipsized() =
        assertEquals(listOf("Supercali…"), TextFit.wrap("Supercalifragilisticexpialidocious", 100f, 2, m))
}
