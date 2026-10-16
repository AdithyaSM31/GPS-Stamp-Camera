package com.gpsstamp.camera.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumQueryTest {
    @Test fun android10Plus_filtersOnRelativePath() =
        assertEquals("relative_path LIKE 'Pictures/GPS Stamp Camera/%'", AlbumQuery.selection(29))

    @Test fun android8and9_filtersOnFilePath() =
        assertEquals("_data LIKE '%/Pictures/GPS Stamp Camera/%'", AlbumQuery.selection(28))
}
