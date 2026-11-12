package nl.petrichor.app.data.media

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaStorageTest {
    @Test
    fun `desktop image paths are converted to file URIs`() {
        val path = File("/tmp/wardrobe images/item.jpg").absolutePath

        assertEquals(File(path).toURI().toString(), imageUriForLoading(path))
    }
}
