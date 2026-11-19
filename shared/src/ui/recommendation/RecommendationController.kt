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
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.data.recommendation.DayWeather
import nl.petrichor.app.data.recommendation.ExplorationPolicy
import nl.petrichor.app.data.recommendation.OutfitCandidate
import nl.petrichor.app.data.recommendation.RankedSlate
import nl.petrichor.app.data.recommendation.RecommendationEngine
import nl.petrichor.app.data.recommendation.RecommendationInput
import nl.petrichor.app.data.recommendation.SharedTrainingCoordinator
import nl.petrichor.app.data.recommendation.dayWeatherFor
import nl.petrichor.app.data.recommendation.tempC
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.data.weather.WeatherSnapshot
import kotlin.time.Clock

private const val STOP_TIMEOUT_MS = 5_000L
private const val MAX_CACHED_SLATES = 16

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

private data class SlateKey(
    val selectedDate: LocalDate,
    val modelDataRevision: Long,
    val laundryVersion: Int,
    val weatherHash: Int,
)

/**
 * Controller for the Suggestions tab: computes session-stable ranked [OutfitCandidate] slates off
 * the Compose UI thread whenever wardrobe, journal, model, laundry, weather, or selected calendar
 * date changes, and logs recommendation impressions for offline evaluation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RecommendationController(
    private val wardrobeRepository: WardrobeRepository =
        WardrobeRepository(trainingCoordinator = SharedTrainingCoordinator.instance),
    private val outfitRepository: OutfitLogRepository =
        OutfitLogRepository(trainingCoordinator = SharedTrainingCoordinator.instance),
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val today: LocalDate = LocalDate.now(),
    private val clock: Clock = Clock.System,
) : ViewModel() {
    val clothingTypes = wardrobeRepository.getClothingTypes()
    val laundryVersion =
        combine(outfitRepository.laundryVersion, wardrobeRepository.laundryVersion) { outfit, piece -> outfit + piece }

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val weatherSnapshotState = MutableStateFlow<WeatherSnapshot?>(null)
    private val slateCache = linkedMapOf<SlateKey, RankedSlate>()
    private val recordedSlateKeys = mutableSetOf<SlateKey>()

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
                val slateKey = buildSlateKey(data, context, dayWeather)
                val cached = slateCache[slateKey]
                if (cached != null) return@mapLatest cached.candidates

                val modelRevision = data.modelState?.dataRevision ?: 0L
                val input = buildInput(data, context, dayWeather, modelRevision)
                val slate = RecommendationEngine.recommendSlateSuspending(input)
                rememberSlate(slateKey, slate)
                recordImpressionIfNeeded(slateKey, slate, input, context.weather?.place?.name)
                slate.candidates
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

    private fun buildSlateKey(
        data: WardrobeAndJournalSnapshot,
        context: RankingContext,
        dayWeather: DayWeather,
    ): SlateKey =
        SlateKey(
            selectedDate = context.selectedDate,
            modelDataRevision = data.modelState?.dataRevision ?: 0L,
            laundryVersion = context.laundryVersion,
            weatherHash =
                computeWeatherHash(
                    dayWeather = dayWeather,
                    locationName = context.weather?.place?.name,
                    pieceCount = data.pieces.size,
                    usableTrainingCount = data.usableTrainingCount,
                ),
        )

    private fun computeWeatherHash(
        dayWeather: DayWeather,
        locationName: String?,
        pieceCount: Int,
        usableTrainingCount: Int,
    ): Int {
        var result = dayWeather.hashCode()
        result = 31 * result + (locationName?.hashCode() ?: 0)
        result = 31 * result + pieceCount
        result = 31 * result + usableTrainingCount
        return result
    }

    private fun buildInput(
        data: WardrobeAndJournalSnapshot,
        context: RankingContext,
        dayWeather: DayWeather,
        modelRevision: Long,
    ): RecommendationInput =
        RecommendationInput(
            date = context.selectedDate,
            today = today,
            tempC = dayWeather.tempC,
            isRainy = dayWeather.isRainy,
            windSpeedMs = dayWeather.windSpeedMs,
            precipitationProbability = dayWeather.precipitationProbability,
            rainVolumeMm = dayWeather.rainVolumeMm,
            wardrobePieces = data.pieces,
            clothingTypes = data.types,
            entries = data.entries,
            surveyedCount = data.usableTrainingCount,
            modelState = data.modelState,
            nowEpochMs = clock.now().toEpochMilliseconds(),
            random = ExplorationPolicy.sessionRandom(context.selectedDate, modelRevision),
        )

    private fun rememberSlate(
        key: SlateKey,
        slate: RankedSlate,
    ) {
        if (slateCache.size >= MAX_CACHED_SLATES) {
            val oldest = slateCache.keys.firstOrNull()
            if (oldest != null) slateCache.remove(oldest)
        }
        slateCache[key] = slate
    }

    private suspend fun recordImpressionIfNeeded(
        slateKey: SlateKey,
        slate: RankedSlate,
        input: RecommendationInput,
        locationName: String?,
    ) {
        if (slate.candidates.isEmpty() || !recordedSlateKeys.add(slateKey)) return
        val candidatesJson = Json.encodeToString(slate.slots)
        val impression =
            ExplorationPolicy.buildImpressionEntity(
                slate = slate,
                input = input,
                locationName = locationName,
                candidatesJson = candidatesJson,
                shownAtEpochMs = clock.now().toEpochMilliseconds(),
            )
        outfitRepository.recordRecommendationImpression(impression)
    }
}
