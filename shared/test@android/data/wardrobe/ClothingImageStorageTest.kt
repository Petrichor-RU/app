package nl.petrichor.app.data.wardrobe

import kotlin.test.Test
import kotlin.test.assertEquals

class ClothingImageStorageTest {
    @Test
    fun `android image paths remain content paths`() {
        val path = "/data/user/0/nl.petrichor.app/files/wardrobe-images/item with spaces.jpg"

        assertEquals(path, imageUriForLoading(path))
    }
}
