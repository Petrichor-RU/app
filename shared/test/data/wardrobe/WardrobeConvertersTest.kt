package nl.petrichor.app.data.wardrobe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WardrobeConvertersTest {
    private val converters = WardrobeConverters()

    @Test
    fun `material conversion round trips every material`() {
        Material.entries.forEach { material ->
            assertEquals(material, converters.stringToMaterial(converters.materialToString(material)))
        }
    }

    @Test
    fun `unknown material values use unknown`() {
        assertEquals(Material.UNKNOWN, converters.stringToMaterial("not-a-material"))
    }

    @Test
    fun `clothing supertype conversion round trips every supertype`() {
        ClothingSupertype.entries.forEach { supertype ->
            assertEquals(
                supertype,
                converters.stringToSupertype(converters.supertypeToString(supertype))
            )
        }
    }

    @Test
    fun `unknown clothing supertype values fail loudly`() {
        assertFailsWith<IllegalStateException> {
            converters.stringToSupertype("not-a-supertype")
        }
    }
}
