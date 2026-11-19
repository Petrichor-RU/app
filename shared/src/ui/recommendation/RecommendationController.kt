package nl.petrichor.app.ui.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kizitonwose.calendar.core.now
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.data.recommendation.OutfitCandidate
import nl.petrichor.app.data.recommendation.RecommendationEngine
import nl.petrichor.app.data.recommendation.RecommendationInput
import nl.petrichor.app.data.recommendation.SharedTrainingCoordinator
import nl.petrichor.app.data.recommendation.dayWeatherFor
import nl.petrichor.app.data.recommendation.tempC
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.data.weather.WeatherSnapshot

private const val STOP_TIMEOUT_MS = 5_000L

private data class WardrobeAndJournalSnapshot(
    val pieces: List<ClothingPiece>,
    val types: List<ClothingType>,
    val entries: List<OutfitEntryWithPieces>,
    val usableTrainingCount: Int,
    val modelState: ModelState?,
)

private data class RankingContext(
    val selectedDate: LocalDate,
    val weather: WeatherSnapshot?,
    val laundryVersion: Int,
)

/**
 * Controller for the Suggestions tab: computes ranked [OutfitCandidate]s off the Compose UI
 * thread whenever wardrobe, journal, model, laundry, weather, or selected calendar date changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecommendationController(
    private val wardrobeRepository: WardrobeRepository =
        WardrobeRepository(trainingCoordinator = SharedTrainingCoordinator.instance),
    private val outfitRepository: OutfitLogRepository =
        OutfitLogRepository(trainingCoordinator = SharedTrainingCoordinator.instance),
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {
    val clothingTypes = wardrobeRepository.getClothingTypes()
    val laundryVersion =
        combine(outfitRepository.laundryVersion, wardrobeRepository.laundryVersion) { outfit, piece -> outfit + piece }

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val weatherSnapshotState = MutableStateFlow<WeatherSnapshot?>(null)

    private val wardrobeAndJournal =
        combine(
            wardrobeRepository.getAllClothing(),
            clothingTypes,
            outfitRepository.entries,
            outfitRepository.usableTrainingCount,
            outfitRepository.modelState,
            ::WardrobeAndJournalSnapshot,
        )

    private val rankingContext =
        combine(
            _selectedDate,
            weatherSnapshotState,
            laundryVersion,
            ::RankingContext,
        )

    val candidates: StateFlow<List<OutfitCandidate>> =
        combine(wardrobeAndJournal, rankingContext) { data, context -> data to context }
            .mapLatest { (data, context) ->
                val dayWeather = context.weather.dayWeatherFor(context.selectedDate, today)
                RecommendationEngine.recommendOutfitsSuspending(
                    RecommendationInput(
                        date = context.selectedDate,
                        today = today,
                        tempC = dayWeather.tempC,
                        isRainy = dayWeather.isRainy,
                        wardrobePieces = data.pieces,
                        clothingTypes = data.types,
                        entries = data.entries,
                        surveyedCount = data.usableTrainingCount,
                        modelState = data.modelState,
                    ),
                )
            }.flowOn(computeDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun updateWeather(weather: WeatherSnapshot?) {
        weatherSnapshotState.value = weather
    }

    fun markPieceLaundry(
        pieceId: Long,
        days: Int,
    ) {
        viewModelScope.launch { wardrobeRepository.markLaundry(pieceId, days) }
    }
}
