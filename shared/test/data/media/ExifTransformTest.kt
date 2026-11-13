package nl.petrichor.app.data.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExifTransformTest {
    @Test
    fun `portrait camera orientation maps to a quarter turn`() {
        assertEquals(ExifTransform(1f, 1f, 90f), exifTransform(6))
        assertEquals(ExifTransform(1f, 1f, 270f), exifTransform(8))
        assertEquals(ExifTransform(1f, 1f, 180f), exifTransform(3))
    }

    @Test
    fun `mirrored orientations map to scales`() {
        assertEquals(ExifTransform(-1f, 1f, 0f), exifTransform(2))
        assertEquals(ExifTransform(1f, -1f, 0f), exifTransform(4))
        assertEquals(ExifTransform(-1f, 1f, 90f), exifTransform(5))
        assertEquals(ExifTransform(-1f, 1f, 270f), exifTransform(7))
    }

    @Test
    fun `upright orientations need no transform`() {
        assertNull(exifTransform(1))
        assertNull(exifTransform(0))
    }
}
