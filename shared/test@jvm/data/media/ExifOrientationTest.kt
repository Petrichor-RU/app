package nl.petrichor.app.data.media

import kotlin.test.Test
import kotlin.test.assertEquals

/** Parser tests for the JVM actual, which reads orientation via the kim library. */
class ExifOrientationTest {
    @Test
    fun `little-endian jpeg exif orientation is read`() {
        assertEquals(6, exifOrientationOf(jpegWithOrientation(6, littleEndian = true)))
        assertEquals(8, exifOrientationOf(jpegWithOrientation(8, littleEndian = true)))
    }

    @Test
    fun `big-endian jpeg exif orientation is read`() {
        assertEquals(6, exifOrientationOf(jpegWithOrientation(6, littleEndian = false)))
        assertEquals(3, exifOrientationOf(jpegWithOrientation(3, littleEndian = false)))
    }

    @Test
    fun `jfif app0 segment before the exif app1 does not confuse the parser`() {
        assertEquals(6, exifOrientationOf(jpegWithOrientation(6, littleEndian = false).withJfifApp0()))
    }

    @Test
    fun `fill bytes before a segment marker are skipped`() {
        assertEquals(6, exifOrientationOf(jpegWithOrientation(6, littleEndian = true).withFillBytesBeforeApp1()))
    }

    @Test
    fun `jpeg without exif segment reports normal orientation`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

        assertEquals(1, exifOrientationOf(jpeg))
    }

    @Test
    fun `non-jpeg bytes report normal orientation`() {
        assertEquals(1, exifOrientationOf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))) // PNG magic
        assertEquals(1, exifOrientationOf(byteArrayOf()))
        assertEquals(1, exifOrientationOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))) // truncated
    }
}
