package nl.petrichor.app.data.wardrobe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClothingTypeTest {
    @Test
    fun `built-in clothing types are unique by supertype and name`() {
        val keys = BuiltInClothingTypes.all.map { it.supertype to it.name }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `built-in clothing types cover every clothing supertype`() {
        assertEquals(
            ClothingSupertype.entries.toSet(),
            BuiltInClothingTypes.all.map { it.supertype }.toSet()
        )
    }

    @Test
    fun `built-in clothing types have generated-id compatible defaults`() {
        assertTrue(BuiltInClothingTypes.all.all { it.id == 0L })
        assertTrue(BuiltInClothingTypes.all.all { it.name.isNotBlank() })
    }
}
