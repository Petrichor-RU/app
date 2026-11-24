package nl.petrichor.app.ui.journal

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.EntryWeatherSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutfitEntryBuilderTest {
    @Test
    fun `buildOutfitEntry copies extended weather snapshot fields onto new and edited entries`() {
        val date = LocalDate(2026, 11, 12)
        val snapshot =
            EntryWeatherSnapshot(
                temperatureC = 11.5,
                feelsLikeC = 9.8,
                minTempC = 7.0,
                maxTempC = 14.0,
                weatherDescription = "Light rain",
                weatherIcon = "10d",
                isRainy = true,
                windSpeedMs = 6.4,
                humidity = 82,
                precipitationProbability = 0.75,
                rainVolumeMm = 2.6,
                weatherCapturedAtEpochMs = 1_794_484_800_000L,
                locationName = "Amsterdam",
            )

        val created =
            buildOutfitEntry(
                existing = null,
                date = date,
                photoPath = "selfie.jpg",
                notes = "Windy walk",
                weatherSnapshot = snapshot,
            )

        assertEquals(date, created.wornOnDate)
        assertEquals("selfie.jpg", created.photoPath)
        assertEquals("Windy walk", created.notes)
        assertEquals(11.5, created.temperatureC)
        assertEquals(9.8, created.feelsLikeC)
        assertEquals(7.0, created.minTempC)
        assertEquals(14.0, created.maxTempC)
        assertEquals("Light rain", created.weatherDescription)
        assertEquals("10d", created.weatherIcon)
        assertTrue(created.isRainy)
        assertEquals(6.4, created.windSpeedMs)
        assertEquals(82, created.humidity)
        assertEquals(0.75, created.precipitationProbability)
        assertEquals(2.6, created.rainVolumeMm)
        assertEquals(1_794_484_800_000L, created.weatherCapturedAtEpochMs)
        assertEquals("Amsterdam", created.locationName)
    }
}
