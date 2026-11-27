package nl.petrichor.app.data.media

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class Base64ImageTest {
    @Test
    fun `decodeBase64Image decodes base64 while ignoring whitespace`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFE.toByte())
        val encoded = Base64.encode(bytes)
        // Chunked literals and pretty-printing introduce whitespace; it must not affect decoding.
        val withWhitespace = encoded.chunked(4).joinToString("\n ")
        assertContentEquals(bytes, decodeBase64Image(withWhitespace))
    }

    @Test
    fun `decodeBase64Image returns null for input that is not valid base64`() {
        assertNull(decodeBase64Image("not valid base64 !!!"))
    }
}
