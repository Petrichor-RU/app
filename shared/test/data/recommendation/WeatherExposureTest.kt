package nl.petrichor.app.data.recommendation

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import nl.petrichor.app.data.wardrobe.WeatherProtection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeatherExposureTest {
    private val date = LocalDate(2026, 11, 19)
    private val jacketType = ClothingType(id = 1L, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
    private val dressType = ClothingType(id = 2L, name = "Dresses", supertype = ClothingSupertype.FULL_BODY)
    private val shoesType = ClothingType(id = 3L, name = "Shoes", supertype = ClothingSupertype.FOOTWEAR)
    private val typeById = listOf(jacketType, dressType, shoesType).associateBy { it.id }

    private fun testPiece(
        id: Long,
        typeId: Long,
        protection: WeatherProtection,
    ) = ClothingPiece(
        id = id,
        name = "piece-$id",
        typeId = typeId,
        material = Material.POLYESTER,
        weatherProtection = protection,
        notes = null,
        imagePath = "",
        thumbnailPath = "",
    )

    @Test
    fun `wind and rain exposure helpers clamp ranges and ignore non-finite values`() {
        assertEquals(0.0, computeWindExposure(null))
        assertEquals(0.0, computeWindExposure(Double.NaN))
        assertEquals(0.0, computeWindExposure(-2.0))
        assertEquals(0.75, computeWindExposure(7.5), 1e-9)
        assertEquals(1.5, computeWindExposure(25.0), 1e-9)

        assertEquals(0.0, computeRainExposure(isRainy = false))
        assertEquals(
            0.0,
            computeRainExposure(
                isRainy = false,
                precipitationProbability = Double.NaN,
                rainVolumeMm = Double.POSITIVE_INFINITY,
            ),
        )
        assertEquals(0.6, computeRainExposure(isRainy = true, precipitationProbability = 0.2, rainVolumeMm = 1.0), 1e-9)
        assertEquals(0.85, computeRainExposure(isRainy = false, precipitationProbability = 0.85, rainVolumeMm = 2.0), 1e-9)
        assertEquals(1.0, computeRainExposure(isRainy = true, precipitationProbability = 0.4, rainVolumeMm = 8.0), 1e-9)
    }

    @Test
    fun `day weather outfit entry and recommendation input share identical wind and rain exposures`() {
        val dayWeather =
            DayWeather(
                isRainy = true,
                currentTemperatureC = 9.0,
                feelsLikeC = 4.0,
                windSpeedMs = 11.0,
                precipitationProbability = 0.82,
                rainVolumeMm = 3.2,
            )
        val outfitEntry =
            OutfitEntry(
                id = 1L,
                wornOnDate = date,
                photoPath = null,
                notes = null,
                temperatureC = 9.0,
                feelsLikeC = 4.0,
                minTempC = 6.0,
                maxTempC = 11.0,
                weatherDescription = "Rain",
                weatherIcon = "10d",
                isRainy = true,
                windSpeedMs = 11.0,
                precipitationProbability = 0.82,
                rainVolumeMm = 3.2,
                surveyFeelingScore = null,
                surveyWarmthVerdict = null,
                surveyedAtEpochMs = null,
            )
        val input =
            RecommendationInput(
                date = date,
                today = date,
                tempC = 9.0,
                isRainy = true,
                windSpeedMs = 11.0,
                precipitationProbability = 0.82,
                rainVolumeMm = 3.2,
                wardrobePieces = emptyList(),
                clothingTypes = emptyList(),
                entries = emptyList(),
                surveyedCount = 0,
                modelState = null,
            )

        assertEquals(dayWeather.windExposure, outfitEntry.windExposure, 1e-12)
        assertEquals(dayWeather.windExposure, input.windExposure, 1e-12)
        assertEquals(dayWeather.rainExposure, outfitEntry.rainExposure, 1e-12)
        assertEquals(dayWeather.rainExposure, input.rainExposure, 1e-12)
    }

    @Test
    fun `shell protection derives from upper or full body pieces only and uses air temperature`() {
        val waterproofBoots = testPiece(1L, shoesType.id, WeatherProtection.WATERPROOF)
        val windShell = testPiece(2L, jacketType.id, WeatherProtection.WIND_RESISTANT)
        val rainDress = testPiece(3L, dressType.id, WeatherProtection.WATERPROOF)

        // Boots alone do not grant upper-body shell protection.
        val bootsOnly = FeatureExtractor.extractRaw(12.0, true, listOf(waterproofBoots), typeById, windSpeedMs = 9.0)
        assertFalse(bootsOnly.hasRainShell)
        assertFalse(bootsOnly.hasWindShell)
        assertEquals(0.0, bootsOnly.rainProtectionScore)
        assertEquals(0.0, bootsOnly.windProtectionScore)
        assertEquals(12.0, bootsOnly.tempC)

        val windOnly = FeatureExtractor.extractRaw(12.0, true, listOf(windShell), typeById, windSpeedMs = 9.0)
        assertFalse(windOnly.hasRainShell)
        assertTrue(windOnly.hasWindShell)
        assertEquals(1.0, windOnly.windProtectionScore)

        val fullBodyRain = FeatureExtractor.extractRaw(12.0, true, listOf(rainDress), typeById, windSpeedMs = 9.0)
        assertTrue(fullBodyRain.hasRainShell)
        assertTrue(fullBodyRain.hasWindShell)
        assertEquals(1.0, fullBodyRain.rainProtectionScore)
    }
}
