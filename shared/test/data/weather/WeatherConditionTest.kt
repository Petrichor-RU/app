package nl.petrichor.app.data.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeatherConditionTest {
    @Test
    fun `documented condition code groups map to their condition`() {
        val expected =
            mapOf(
                200 to WeatherCondition.THUNDERSTORM,
                232 to WeatherCondition.THUNDERSTORM,
                300 to WeatherCondition.RAIN, // drizzle
                321 to WeatherCondition.RAIN,
                500 to WeatherCondition.RAIN,
                511 to WeatherCondition.RAIN, // freezing rain
                531 to WeatherCondition.RAIN,
                600 to WeatherCondition.SNOW,
                615 to WeatherCondition.SNOW, // rain and snow
                622 to WeatherCondition.SNOW,
                701 to WeatherCondition.FOG, // mist
                741 to WeatherCondition.FOG, // fog
                781 to WeatherCondition.FOG, // tornado
                800 to WeatherCondition.CLEAR,
                801 to WeatherCondition.PARTLY_CLOUDY,
                802 to WeatherCondition.OVERCAST,
                804 to WeatherCondition.OVERCAST,
            )
        for ((code, condition) in expected) {
            assertEquals(condition, WeatherConditions.fromConditionId(code), "code $code")
        }
    }

    @Test
    fun `unknown or missing condition codes have no condition`() {
        assertNull(WeatherConditions.fromConditionId(999))
        assertNull(WeatherConditions.fromConditionId(null))
    }

    @Test
    fun `documented icon list maps to its condition for day and night variants`() {
        val expected =
            mapOf(
                "01" to WeatherCondition.CLEAR,
                "02" to WeatherCondition.PARTLY_CLOUDY,
                "03" to WeatherCondition.OVERCAST,
                "04" to WeatherCondition.OVERCAST,
                "09" to WeatherCondition.RAIN,
                "10" to WeatherCondition.RAIN,
                "11" to WeatherCondition.THUNDERSTORM,
                "13" to WeatherCondition.SNOW,
                "50" to WeatherCondition.FOG,
            )
        for ((prefix, condition) in expected) {
            assertEquals(condition, WeatherConditions.fromIconCode(prefix + "d"))
            assertEquals(condition, WeatherConditions.fromIconCode(prefix + "n"))
        }
        assertNull(WeatherConditions.fromIconCode("99d"))
        assertNull(WeatherConditions.fromIconCode(""))
        assertNull(WeatherConditions.fromIconCode(null))
    }

    @Test
    fun `condition code wins over icon code when both are present`() {
        // 511 "freezing rain" is group 5xx even though the API draws it with the snow icon "13d"
        assertEquals(WeatherCondition.RAIN, WeatherConditions.from(511, "13d"))
        assertEquals(WeatherCondition.CLEAR, WeatherConditions.from(800, "01n"))
        assertEquals(WeatherCondition.SNOW, WeatherConditions.from(null, "13d"))
        assertNull(WeatherConditions.from(null, null))
    }

    @Test
    fun `rainy detection covers drizzle rain thunderstorm and description text`() {
        for (code in listOf(300, 500, 511, 200)) {
            assertTrue(WeatherConditions.isRainy(null, code, null), "code $code should be rainy")
        }
        for (code in listOf(600, 700, 800, 801, 804)) {
            assertFalse(WeatherConditions.isRainy(null, code, null), "code $code should not be rainy")
        }
        assertTrue(WeatherConditions.isRainy(null, null, "09d"))
        assertTrue(WeatherConditions.isRainy(null, null, "11n"))
        assertFalse(WeatherConditions.isRainy(null, null, "13d"))
        assertTrue(WeatherConditions.isRainy("Light rain", 800, "01d"))
        assertTrue(WeatherConditions.isRainy("Rain and snow", null, "13d"))
        assertFalse(WeatherConditions.isRainy("Clear sky", 800, "01d"))
        assertFalse(WeatherConditions.isRainy(null, null, null))
    }
}
