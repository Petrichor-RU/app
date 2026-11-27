package nl.petrichor.app.data.affiliate

import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.ForecastDay
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShoppingContextTest {
    private fun snapshot(
        temperature: Int,
        conditionId: Int = 800,
        icon: String = "01d",
        description: String = "Clear sky",
        forecast: List<ForecastDay> = emptyList(),
    ) = WeatherSnapshot(
        place = Place("Test", Coordinates(0.0, 0.0)),
        temperature = temperature,
        feelsLike = temperature,
        description = description,
        icon = icon,
        conditionId = conditionId,
        humidity = 50,
        windSpeed = 10,
        forecast = forecast,
    )

    private fun day(
        min: Int,
        max: Int = min + 5,
        conditionId: Int = 800,
        icon: String = "01d",
    ) = ForecastDay(label = "2026-11-24", min = min, max = max, icon = icon, conditionId = conditionId)

    @Test
    fun `null weather degrades to the neutral default mild and not rainy`() {
        val context = deriveShoppingContext(null)
        assertEquals(TemperatureBand.MILD, context.temperatureBand)
        assertFalse(context.rainExpected)
    }

    @Test
    fun `rain on any forecast day sets rainExpected`() {
        val context =
            deriveShoppingContext(
                snapshot(temperature = 25, forecast = listOf(day(20, conditionId = 500, icon = "10d"), day(22))),
            )
        assertTrue(context.rainExpected)
    }

    @Test
    fun `rain today by description sets rainExpected`() {
        val context = deriveShoppingContext(snapshot(temperature = 18, description = "Light rain", icon = "01d", conditionId = 800))
        assertTrue(context.rainExpected)
    }

    @Test
    fun `coldest forecast minimum wins over the current temperature band`() {
        // Warm now (WARM), but a cold snap three days out (min 2°C → COLD) must win.
        val context =
            deriveShoppingContext(
                snapshot(temperature = 26, forecast = listOf(day(20), day(15), day(2), day(18))),
            )
        assertEquals(TemperatureBand.COLD, context.temperatureBand)
    }

    @Test
    fun `isolated warm day in winter does not surface warm band`() {
        val context =
            deriveShoppingContext(
                snapshot(temperature = 3, forecast = listOf(day(1), day(24), day(2))),
            )
        assertEquals(TemperatureBand.COLD, context.temperatureBand)
    }

    @Test
    fun `band thresholds are warm at 22 mild at 15 cool at 8 cold below`() {
        assertEquals(TemperatureBand.WARM, TemperatureBand.fromCelsius(22))
        assertEquals(TemperatureBand.WARM, TemperatureBand.fromCelsius(30))
        assertEquals(TemperatureBand.MILD, TemperatureBand.fromCelsius(15))
        assertEquals(TemperatureBand.MILD, TemperatureBand.fromCelsius(21))
        assertEquals(TemperatureBand.COOL, TemperatureBand.fromCelsius(8))
        assertEquals(TemperatureBand.COOL, TemperatureBand.fromCelsius(14))
        assertEquals(TemperatureBand.COLD, TemperatureBand.fromCelsius(7))
        assertEquals(TemperatureBand.COLD, TemperatureBand.fromCelsius(-5))
    }

    @Test
    fun `target types follow the weather table`() {
        assertEquals(
            listOf("Hoodies", "Sweaters", "Jackets"),
            ShoppingContext(rainExpected = false, temperatureBand = TemperatureBand.COLD).primaryTypeNames,
        )
        assertEquals(
            listOf("Jackets", "Pants", "Jeans"),
            ShoppingContext(rainExpected = true, temperatureBand = TemperatureBand.COOL).primaryTypeNames,
        )
        assertEquals(
            listOf("Shirts", "Sweaters"),
            ShoppingContext(rainExpected = false, temperatureBand = TemperatureBand.MILD).primaryTypeNames,
        )
        assertEquals(
            listOf("T-Shirts", "Shorts"),
            ShoppingContext(rainExpected = false, temperatureBand = TemperatureBand.WARM).primaryTypeNames,
        )
        assertEquals(
            listOf("T-Shirts", "Pants"),
            ShoppingContext(rainExpected = true, temperatureBand = TemperatureBand.WARM).primaryTypeNames,
        )
        // Accessories/headwear/footwear may always fill unused slots.
        assertEquals(
            listOf("Sneakers", "Caps", "Ties"),
            ShoppingContext(rainExpected = false, temperatureBand = TemperatureBand.MILD).fillerTypeNames,
        )
    }
}
