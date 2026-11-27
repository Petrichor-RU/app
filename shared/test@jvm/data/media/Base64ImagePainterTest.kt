package nl.petrichor.app.data.media

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Base64ImagePainterTest {
    // 1x1 pixel PNG
    private val pngBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="

    @Test
    fun `loadBase64ImagePainter decodes a valid bundled image into a painter without network`() =
        runTest {
            assertNotNull(loadBase64ImagePainter(pngBase64))
        }

    @Test
    fun `loadBase64ImagePainter returns null for undecodable input`() =
        runTest {
            assertNull(loadBase64ImagePainter("not valid base64 !!!"))
            assertNull(loadBase64ImagePainter("aGVsbG8=")) // valid base64, but not an image
        }
}
