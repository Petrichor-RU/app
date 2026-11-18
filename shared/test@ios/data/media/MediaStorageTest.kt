package nl.petrichor.app.data.media

import kotlin.test.Test
import kotlin.test.assertTrue

class MediaStorageTest {
    @Test
    fun `ios image paths are converted to document file URLs`() {
        val uri = imageUriForLoading("wardrobe-images/item with spaces.jpg")

        assertTrue(uri.startsWith("file://"))
        assertTrue(uri.contains("/wardrobe-images/item%20with%20spaces.jpg"))
    }
}
