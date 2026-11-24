package nl.petrichor.app.data.recommendation

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import nl.petrichor.app.data.wardrobe.WeatherProtection

/** Shared wardrobe and surveyed-journal fixtures for active-learning and exploration tests. */
internal object SurveyHistoryFixtures {
    val baseDate = LocalDate(2026, 9, 1)
    val teeType = ClothingType(id = 1L, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    val shirtType = ClothingType(id = 2L, name = "Shirts", supertype = ClothingSupertype.UPPER_BODY)
    val sweaterType = ClothingType(id = 3L, name = "Sweaters", supertype = ClothingSupertype.UPPER_BODY)
    val jacketType = ClothingType(id = 4L, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
    val jeansType = ClothingType(id = 5L, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
    val shortsType = ClothingType(id = 6L, name = "Shorts", supertype = ClothingSupertype.LOWER_BODY)
    val types = listOf(teeType, shirtType, sweaterType, jacketType, jeansType, shortsType)

    fun piece(
        id: Long,
        typeId: Long,
        material: Material = Material.COTTON,
        protection: WeatherProtection = WeatherProtection.NONE,
    ) = ClothingPiece(
        id = id,
        name = "p$id",
        typeId = typeId,
        material = material,
        weatherProtection = protection,
        notes = null,
        imagePath = "",
        thumbnailPath = "",
    )

    @Suppress("LongParameterList") // concise test builder for surveyed journal entries
    fun surveyedEntry(
        id: Long,
        dayOffset: Int,
        tempC: Double,
        pieces: List<ClothingPiece>,
        score: Int,
        verdict: WarmthVerdict,
        isRainy: Boolean = false,
        pop: Double? = null,
        rainMm: Double? = null,
        mostlyIndoors: Boolean? = null,
        highActivity: Boolean? = null,
        gotWet: Boolean? = null,
    ): OutfitEntryWithPieces {
        val ts = 1_790_000_000_000L + dayOffset * 86_400_000L
        return OutfitEntryWithPieces(
            entry =
                OutfitEntry(
                    id = id,
                    wornOnDate = baseDate.plus(DatePeriod(days = dayOffset)),
                    photoPath = null,
                    notes = null,
                    temperatureC = tempC,
                    feelsLikeC = tempC,
                    minTempC = tempC - 2.0,
                    maxTempC = tempC + 2.0,
                    weatherDescription = if (isRainy) "Rain" else "Clear",
                    weatherIcon = if (isRainy) "10d" else "01d",
                    isRainy = isRainy,
                    precipitationProbability = pop,
                    rainVolumeMm = rainMm,
                    surveyFeelingScore = score,
                    surveyWarmthVerdict = verdict,
                    surveyedAtEpochMs = ts,
                    mostlyIndoors = mostlyIndoors,
                    highActivity = highActivity,
                    gotWet = gotWet,
                    createdAtEpochMs = ts,
                ),
            pieces = pieces,
        )
    }

    fun standardWardrobe(): List<ClothingPiece> =
        listOf(
            piece(1L, teeType.id, Material.LINEN),
            piece(2L, shirtType.id, Material.COTTON),
            piece(3L, sweaterType.id, Material.WOOL),
            piece(4L, jacketType.id, Material.WOOL),
            piece(5L, jeansType.id, Material.COTTON),
            piece(6L, shortsType.id, Material.LINEN),
        )

    fun buildRichHistory(wardrobe: List<ClothingPiece>): List<OutfitEntryWithPieces> {
        val tee = wardrobe[0]
        val shirt = wardrobe[1]
        val sweater = wardrobe[2]
        val jacket = wardrobe[3]
        val jeans = wardrobe[4]
        val shorts = wardrobe[5]
        return listOf(
            surveyedEntry(1L, 0, 26.0, listOf(tee, shorts), 5, WarmthVerdict.PERFECT),
            surveyedEntry(2L, 1, 24.0, listOf(tee, shorts), 5, WarmthVerdict.PERFECT),
            surveyedEntry(3L, 2, 18.0, listOf(shirt, jeans), 5, WarmthVerdict.PERFECT),
            surveyedEntry(4L, 3, 17.0, listOf(shirt, jeans), 4, WarmthVerdict.PERFECT),
            surveyedEntry(5L, 4, 12.0, listOf(sweater, jeans), 5, WarmthVerdict.PERFECT),
            surveyedEntry(6L, 5, 11.0, listOf(sweater, jeans), 5, WarmthVerdict.PERFECT),
            surveyedEntry(7L, 6, 6.0, listOf(jacket, sweater, jeans), 5, WarmthVerdict.PERFECT),
            surveyedEntry(8L, 7, 5.0, listOf(jacket, sweater, jeans), 5, WarmthVerdict.PERFECT),
            surveyedEntry(9L, 8, 5.0, listOf(tee, shorts), 1, WarmthVerdict.TOO_COLD),
            surveyedEntry(10L, 9, 4.0, listOf(tee, shorts), 1, WarmthVerdict.TOO_COLD),
            surveyedEntry(11L, 10, 26.0, listOf(jacket, sweater, jeans), 1, WarmthVerdict.TOO_WARM),
            surveyedEntry(12L, 11, 25.0, listOf(jacket, sweater, jeans), 1, WarmthVerdict.TOO_WARM),
        )
    }
}
