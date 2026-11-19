package nl.petrichor.app.data.database

import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.Material
import nl.petrichor.app.data.wardrobe.WeatherProtection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PetrichorConvertersTest {
    private val converters = PetrichorConverters()

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
                converters.stringToSupertype(converters.supertypeToString(supertype)),
            )
        }
    }

    @Test
    fun `unknown clothing supertype values fail loudly`() {
        assertFailsWith<IllegalStateException> {
            converters.stringToSupertype("not-a-supertype")
        }
    }

    @Test
    fun `weather protection conversion round trips every value`() {
        WeatherProtection.entries.forEach { protection ->
            assertEquals(
                protection,
                converters.stringToWeatherProtection(converters.weatherProtectionToString(protection)),
            )
        }
    }

    @Test
    fun `unknown weather protection values fall back to none`() {
        assertEquals(WeatherProtection.NONE, converters.stringToWeatherProtection("not-a-protection"))
    }
}
