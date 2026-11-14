package nl.petrichor.app.ui.recommendation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kizitonwose.calendar.core.now
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.data.recommendation.RecommendationEngine
import nl.petrichor.app.data.recommendation.RecommendationInput
import nl.petrichor.app.data.recommendation.dayWeatherFor
import nl.petrichor.app.data.recommendation.forecastDates
import nl.petrichor.app.data.recommendation.tempC
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.background.AppWeatherState

private class RecommendationController : ViewModel() {
    private val wardrobeRepository = WardrobeRepository()
    private val outfitRepository = OutfitLogRepository()

    val allClothing = wardrobeRepository.getAllClothing()
    val clothingTypes = wardrobeRepository.getClothingTypes()
    val entries = outfitRepository.entries
    val surveyedCount = outfitRepository.surveyedCount
    val modelState = outfitRepository.modelState
    val laundryVersion =
        combine(outfitRepository.laundryVersion, wardrobeRepository.laundryVersion) { outfit, piece -> outfit + piece }

    fun markPieceLaundry(
        pieceId: Long,
        days: Int,
    ) {
        viewModelScope.launch { wardrobeRepository.markLaundry(pieceId, days) }
    }
}

/**
 * Suggestions tab (plan section 7): the calendar stays on top and the screen opens scrolled
 * below it to the top-3 outfit candidates in a horizontal pager. Weather and location name come
 * from the app-shell-shared [AppWeatherState] (loaded once per place in App.kt) rather than a
 * private request, so this tab never issues its own weather load.
 */
@Composable
fun RecommendationScreen(
    weatherState: AppWeatherState,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        BoxMessage(message = "Recommendation preview", modifier = modifier.fillMaxSize())
        return
    }

    val controller = viewModel { RecommendationController() }
    val locationName = weatherState.place?.name ?: "Finding your location..."
    val weather = weatherState.weather
    val allClothing by controller.allClothing.collectAsStateWithLifecycle(emptyList())
    val clothingTypes by controller.clothingTypes.collectAsStateWithLifecycle(emptyList())
    val entries by controller.entries.collectAsStateWithLifecycle(emptyList())
    val surveyedCount by controller.surveyedCount.collectAsStateWithLifecycle(0)
    val modelState by controller.modelState.collectAsStateWithLifecycle(null)
    val laundryVersion by controller.laundryVersion.collectAsStateWithLifecycle(0)
    val today = remember { LocalDate.now() }
    val candidates =
        remember(allClothing, clothingTypes, entries, surveyedCount, modelState, weather, laundryVersion, today) {
            val dayWeather = weather.dayWeatherFor(today, today)
            RecommendationEngine.recommendOutfits(
                RecommendationInput(
                    date = today,
                    today = today,
                    tempC = dayWeather.tempC,
                    isRainy = dayWeather.isRainy,
                    wardrobePieces = allClothing,
                    clothingTypes = clothingTypes,
                    entries = entries,
                    surveyedCount = surveyedCount,
                    modelState = modelState,
                ),
            )
        }

    RecommendationContent(
        modifier = modifier,
        uiState =
            RecommendationUiState(
                locationName = locationName,
                weather = weather,
                today = today,
                candidates = candidates,
                clothingTypes = clothingTypes,
                laundryVersion = laundryVersion,
            ),
        onMarkPieceLaundry = { pieceId, days -> controller.markPieceLaundry(pieceId, days) },
    )
}

private const val CALENDAR_PULL_REVEAL_DP = 96f

/**
 * Reveals the calendar top bar when the user pulls down at the very top of the suggestions list;
 * scrolling down hides it again.
 */
internal class CalendarHeaderPullController(
    private val atTop: () -> Boolean,
    density: Density,
) {
    var headerVisible by mutableStateOf(false)
        private set
    private val revealThreshold = with(density) { CALENDAR_PULL_REVEAL_DP.dp.toPx() }
    private var pullAccum = 0f

    val connection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y > 0f && atTop()) {
                    pullAccum += available.y
                    if (!headerVisible && pullAccum >= revealThreshold) {
                        headerVisible = true
                        pullAccum = 0f
                    }
                    return available
                }
                if (available.y < 0f) pullAccum = 0f
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y != 0f) pullAccum = 0f
                if (consumed.y < 0f) headerVisible = false
                return Offset.Zero
            }
        }
}

@Composable
private fun rememberCalendarHeaderPull(listState: LazyListState): CalendarHeaderPullController {
    val density = LocalDensity.current
    return remember(listState, density) { CalendarHeaderPullController({ !listState.canScrollBackward }, density) }
}

/**
 * Testable content of the suggestions tab: calendar on top (item 0), candidates below (item 1);
 * opens scrolled below the calendar.
 */
@Composable
internal fun RecommendationContent(
    modifier: Modifier = Modifier,
    uiState: RecommendationUiState,
    onMarkPieceLaundry: (pieceId: Long, days: Int) -> Unit,
) {
    var selectedDateIso by rememberSaveable { mutableStateOf(uiState.today.toString()) }
    val selectedDate = LocalDate.parse(selectedDateIso)
    val dayWeather = uiState.weather.dayWeatherFor(selectedDate, uiState.today)

    // Opens scrolled below the calendar (item 1); the lazy-list state saver keeps the position.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 1)
    val headerPull = rememberCalendarHeaderPull(listState)

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .nestedScroll(headerPull.connection)
                .testTag("recommendation_list"),
        state = listState,
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            RecommendationCalendar(
                selectedDate = selectedDate,
                today = uiState.today,
                onDateSelected = { date -> selectedDateIso = date.toString() },
                forecastDates = uiState.weather?.forecastDates(uiState.today).orEmpty(),
                headerVisible = headerPull.headerVisible,
            )
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LocationNote(locationName = uiState.locationName, dayWeather = dayWeather)
                Spacer(Modifier.height(16.dp))
                CandidatePager(
                    candidates = uiState.candidates,
                    clothingTypes = uiState.clothingTypes,
                    onMarkPieceLaundry = onMarkPieceLaundry,
                )
            }
        }
    }
}
