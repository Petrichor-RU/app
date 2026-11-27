package nl.petrichor.app.data.affiliate

import nl.petrichor.app.data.wardrobe.ClothingSupertype
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrapeUtilsTest {
    @Test
    fun `maps store taxonomy onto built-in types`() {
        assertEquals("Hoodies" to ClothingSupertype.UPPER_BODY, mapToBuiltInType("Men Full Sleeve Hooded Sweatshirt"))
        assertEquals("Sweaters" to ClothingSupertype.UPPER_BODY, mapToBuiltInType("Wool sweater"))
        assertEquals("Jackets" to ClothingSupertype.UPPER_BODY, mapToBuiltInType("Rain jacket"))
        assertEquals("T-Shirts" to ClothingSupertype.UPPER_BODY, mapToBuiltInType("Cotton T-Shirt"))
        assertEquals("Shirts" to ClothingSupertype.UPPER_BODY, mapToBuiltInType("Oxford shirt"))
        assertEquals("Jeans" to ClothingSupertype.LOWER_BODY, mapToBuiltInType("Slim fit jeans"))
        assertEquals("Pants" to ClothingSupertype.LOWER_BODY, mapToBuiltInType("Cargo pants"))
        assertEquals("Shorts" to ClothingSupertype.LOWER_BODY, mapToBuiltInType("Denim shorts"))
        assertEquals("Sneakers" to ClothingSupertype.FOOTWEAR, mapToBuiltInType("Running shoes"))
        assertEquals("Caps" to ClothingSupertype.HEADWEAR, mapToBuiltInType("Baseball cap"))
        assertEquals("Ties" to ClothingSupertype.ACCESSORIES, mapToBuiltInType("Silk tie"))
    }

    @Test
    fun `unmappable items are dropped`() {
        assertNull(mapToBuiltInType("USB cable"))
        assertNull(mapToBuiltInType(""))
    }

    @Test
    fun `t-shirt wins over the shirt substring and sweatshirt maps to sweaters not shirts`() {
        assertEquals("T-Shirts", mapToBuiltInType("graphic tee t-shirt")?.first)
        assertEquals("Sweaters", mapToBuiltInType("sweatshirt")?.first)
    }

    @Test
    fun `extracts balanced json objects honouring string escapes`() {
        val html = """prefix {"a":{"b":"x}y{"},"c":[1,2]} suffix"""
        assertEquals("""{"a":{"b":"x}y{"},"c":[1,2]}""", extractJsonObject(html, "prefix"))
        assertNull(extractJsonObject(html, "missing-marker"))
    }
}
