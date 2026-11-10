package nl.petrichor.app.data.recommendation

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.weather.WeatherSnapshot

internal data class DayWeather(
    val isRainy: Boolean = false,
    val minTemperature: Int? = null,
    val maxTemperature: Int? = null,
)

internal data class LayerRecommendation(
    val label: String,
    val options: List<ClothingPiece>,
    val generalSuggestion: String,
)

internal data class OutfitRecommendation(
    val headwear: LayerRecommendation,
    val baseUpper: LayerRecommendation,
    val outerUpper: LayerRecommendation,
    val lowerBody: LayerRecommendation,
    val footwear: LayerRecommendation,
)

// TODO: Standardize outer outfit types somewhere else
private val outerUpperTypeNames = setOf("Jackets", "Hoodies", "Sweaters")

internal fun WeatherSnapshot.forecastDates(today: LocalDate): Set<LocalDate> =
    forecast.mapNotNull { runCatching { LocalDate.parse(it.label) }.getOrNull() }.toSet() + today

internal fun WeatherSnapshot?.dayWeatherFor(date: LocalDate, today: LocalDate): DayWeather {
    if (this == null) return DayWeather()
    val forecastDay = forecast.firstOrNull { it.label == date.toString() }
    if (date != today && forecastDay == null) return DayWeather()
    val isRainy = (date == today && description.contains("rain", ignoreCase = true)) ||
        forecastDay?.icon?.take(2) in setOf("09", "10", "11") // Checks the OpenWeatherMap icon code for rain
    return DayWeather(
        isRainy = isRainy,
        minTemperature = if (date == today) minOf(forecastDay?.min ?: temperature, temperature) else forecastDay?.min,
        maxTemperature = if (date == today) maxOf(forecastDay?.max ?: temperature, temperature) else forecastDay?.max,
    )
}

// TODO: Replace with recommendation ranking logic based on weather.
internal fun recommendOutfit(
    clothing: List<ClothingPiece>,
    clothingTypes: List<ClothingType>,
    weather: DayWeather,
): OutfitRecommendation {
    val typeById = clothingTypes.associateBy { it.id }
    val bySupertype = clothing.groupBy { typeById[it.typeId]?.supertype }
    val (outerUpper, baseUpper) = bySupertype[ClothingSupertype.UPPER_BODY].orEmpty()
        .partition { typeById[it.typeId]?.name in outerUpperTypeNames }

    return OutfitRecommendation(
        headwear = LayerRecommendation(
            label = "Hat",
            options = bySupertype[ClothingSupertype.HEADWEAR].orEmpty(),
            generalSuggestion = if (weather.isRainy) "Rain hat / Cap" else "Cap / Hat",
        ),
        baseUpper = LayerRecommendation(
            label = "Base layer",
            options = baseUpper,
            generalSuggestion = "T-Shirt / Shirt",
        ),
        outerUpper = LayerRecommendation(
            label = "Outer layer",
            options = outerUpper,
            generalSuggestion = if (weather.isRainy) "Rain jacket" else "Jacket / Sweater",
        ),
        lowerBody = LayerRecommendation(
            label = "Bottoms",
            options = bySupertype[ClothingSupertype.LOWER_BODY].orEmpty(),
            generalSuggestion = if (weather.isRainy) "Long pants" else "Jeans / Pants",
        ),
        footwear = LayerRecommendation(
            label = "Shoes",
            options = bySupertype[ClothingSupertype.FOOTWEAR].orEmpty(),
            generalSuggestion = if (weather.isRainy) "Waterproof shoes" else "Sneakers",
        ),
    )
}
