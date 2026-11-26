package nl.petrichor.app.ui.recommendation

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.recommendation.OutfitCandidate
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.weather.WeatherSnapshot

/** Plain UI state for [RecommendationContent], bundled so tests can drive the screen directly. */
internal class RecommendationUiState(
    val locationName: String,
    val weather: WeatherSnapshot?,
    val today: LocalDate,
    val candidates: List<OutfitCandidate>,
    val clothingTypes: List<ClothingType>,
    val laundryVersion: Int,
)
