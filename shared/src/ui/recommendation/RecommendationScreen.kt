package nl.petrichor.app.ui.recommendation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import nl.petrichor.app.data.currentPlace
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherRepository
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.wardrobe.ClothingCard
import kotlin.time.Clock

private sealed interface RecommendationWeatherState {
    data object Loading : RecommendationWeatherState
    data class Ready(val weather: WeatherSnapshot) : RecommendationWeatherState
    data class Error(val message: String) : RecommendationWeatherState
}

/** Weather prediction for a single calendar day. */
internal data class DayWeather(
    val date: LocalDate,
    val isRainy: Boolean = false,
    val minTemperature: Int? = null,
    val maxTemperature: Int? = null,
)

/**
 * A single swipeable slot within a body row (for example, a base shirt or an outer jacket).
 * Swiping cycles through [options] for just this slot without changing the rest of the outfit.
 */
internal data class OutfitLayerSlot(
    val label: String,
    val options: List<ClothingPiece>,
    val generalSuggestion: String,
)

/**
 * A horizontal body row in top-to-bottom order (Headwear -> Upper body -> Lower body -> Footwear).
 * Can hold multiple [layers] side by side (e.g., base layer + outer layer for upper body).
 */
internal data class OutfitBodyRow(
    val title: String,
    val layers: List<OutfitLayerSlot>,
)

private val outerUpperTypeNames = setOf("Jackets", "Hoodies", "Sweaters")

private class RecommendationViewModel : ViewModel() {
    private val weatherRepository = WeatherRepository()
    private val wardrobeRepository = WardrobeRepository()
    val allClothing = wardrobeRepository.getAllClothing()
    val clothingTypes = wardrobeRepository.getClothingTypes()

    private val _place = MutableStateFlow<Place?>(null)
    val place: StateFlow<Place?> = _place.asStateFlow()

    private val _weather = MutableStateFlow<RecommendationWeatherState>(RecommendationWeatherState.Loading)
    val weather: StateFlow<RecommendationWeatherState> = _weather.asStateFlow()

    fun loadWeather(place: Place) {
        _place.value = place
        _weather.value = RecommendationWeatherState.Loading
        viewModelScope.launch {
            _weather.value = weatherRepository.load(place).fold(
                onSuccess = { RecommendationWeatherState.Ready(it) },
                onFailure = { RecommendationWeatherState.Error(it.message ?: "Unable to load weather.") },
            )
        }
    }
}

@Composable
fun RecommendationScreen(
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        BoxMessage(
            message = "Recommendation preview",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val viewModel = remember { RecommendationViewModel() }
    val place by viewModel.place.collectAsStateWithLifecycle()
    val weatherState by viewModel.weather.collectAsStateWithLifecycle()
    val allClothing by viewModel.allClothing.collectAsStateWithLifecycle(emptyList())
    val clothingTypes by viewModel.clothingTypes.collectAsStateWithLifecycle(emptyList())
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var selectedDateIso by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDate = LocalDate.parse(selectedDateIso)

    LaunchedEffect(Unit) {
        if (place == null) {
            viewModel.loadWeather(currentPlace())
        }
    }

    val weather = (weatherState as? RecommendationWeatherState.Ready)?.weather
    val dayWeather = weather.dayWeatherFor(selectedDate, today)
    val bodyRows = remember(allClothing, clothingTypes, dayWeather) {
        recommendOutfitRows(allClothing, clothingTypes, dayWeather)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecommendationCalendar(
            selectedDate = selectedDate,
            today = today,
            onDateSelected = { date -> selectedDateIso = date.toString() },
            forecastDates = weather?.forecastDates(today).orEmpty(),
        )

        Spacer(Modifier.height(14.dp))

        Text(
            if (selectedDate == today) "Recommended for today" else "Recommended for ${selectedDate.formatted()}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        LocationNote(
            locationName = weather?.place?.name ?: place?.name ?: "Finding your location...",
            dayWeather = dayWeather,
        )

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            bodyRows.forEach { row ->
                OutfitBodyRowSection(
                    row = row,
                    resetKey = selectedDateIso,
                )
            }
        }
    }
}

@Composable
private fun LocationNote(
    locationName: String,
    dayWeather: DayWeather,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbol(
            iconName = MaterialSymbols.LOCATION_ON,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        val tempSuffix = if (dayWeather.maxTemperature != null && dayWeather.minTemperature != null) {
            " • ${dayWeather.maxTemperature}° / ${dayWeather.minTemperature}°"
        } else {
            ""
        }
        Text(
            text = "Based on $locationName$tempSuffix",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OutfitBodyRowSection(
    row: OutfitBodyRow,
    resetKey: Any,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top,
        ) {
            row.layers.forEach { slot ->
                SwipeableClothingSlot(
                    slot = slot,
                    showLayerLabel = row.layers.size > 1,
                    resetKey = resetKey,
                )
            }
        }
    }
}

@Composable
private fun SwipeableClothingSlot(
    slot: OutfitLayerSlot,
    showLayerLabel: Boolean,
    resetKey: Any,
) {
    val pageCount = slot.options.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()
    // Prevents horizontal drags on a clothing slot from swiping the outer App tab pager
    val consumeHorizontalScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(available.x, 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(available.x, 0f)
        }
    }

    LaunchedEffect(resetKey, pageCount) {
        pagerState.scrollToPage(0)
    }

    Column(
        modifier = Modifier.width(136.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showLayerLabel) {
            Text(
                text = slot.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .size(132.dp)
                .nestedScroll(consumeHorizontalScroll),
        ) { page ->
            val clothing = slot.options.getOrNull(page)
            if (clothing != null) {
                ClothingCard(
                    clothing = clothing,
                    onClick = {
                        if (slot.options.size > 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage + 1) % slot.options.size)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                GeneralRecommendationSlotCard(
                    suggestion = slot.generalSuggestion,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (slot.options.size > 1) {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val prev = (pagerState.currentPage - 1 + slot.options.size) % slot.options.size
                            pagerState.animateScrollToPage(prev)
                        }
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.CHEVRON_LEFT,
                        contentDescription = "Previous ${slot.label}",
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(slot.options.size.coerceAtMost(5)) { index ->
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    }
                                )
                        )
                    }
                }
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val next = (pagerState.currentPage + 1) % slot.options.size
                            pagerState.animateScrollToPage(next)
                        }
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.CHEVRON_RIGHT,
                        contentDescription = "Next ${slot.label}",
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneralRecommendationSlotCard(
    suggestion: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MaterialSymbol(
                iconName = MaterialSymbols.CHECKROOM,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = suggestion,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Dates covered by the forecast, today included since current weather is always known. */
private fun WeatherSnapshot.forecastDates(today: LocalDate): Set<LocalDate> =
    forecast.mapNotNull { runCatching { LocalDate.parse(it.label) }.getOrNull() }.toSet() + today

private fun WeatherSnapshot?.dayWeatherFor(
    date: LocalDate,
    today: LocalDate,
): DayWeather {
    if (this == null) return DayWeather(date = date)
    val forecastDay = forecast.firstOrNull { it.label == date.toString() }
    val isRainy = (date == today && description.contains("rain", ignoreCase = true)) ||
        forecastDay?.icon?.startsWith("09") == true ||
        forecastDay?.icon?.startsWith("10") == true ||
        forecastDay?.icon?.startsWith("11") == true
    return when {
        date == today -> DayWeather(
            date = date,
            isRainy = isRainy,
            minTemperature = minOf(forecastDay?.min ?: temperature, temperature),
            maxTemperature = maxOf(forecastDay?.max ?: temperature, temperature),
        )
        forecastDay != null -> DayWeather(
            date = date,
            isRainy = isRainy,
            minTemperature = forecastDay.min,
            maxTemperature = forecastDay.max,
        )
        else -> DayWeather(date = date)
    }
}

/**
 * Builds top-to-bottom body rows (Headwear -> Upper body -> Lower body -> Footwear).
 * Upper body supports side-by-side layering (base layer next to outer layer), and each slot holds
 * its own list of wardrobe options so the user can swipe individual items independently.
 * TODO: Replace with full recommendation ranking logic based on [weather].
 */
internal fun recommendOutfitRows(
    clothing: List<ClothingPiece>,
    clothingTypes: List<ClothingType>,
    weather: DayWeather,
): List<OutfitBodyRow> {
    val typeById = clothingTypes.associateBy { it.id }
    val bySupertype = clothing.groupBy { typeById[it.typeId]?.supertype }
    val upperItems = bySupertype[ClothingSupertype.UPPER_BODY].orEmpty()
    val (outerUpper, baseUpper) = upperItems.partition { piece ->
        typeById[piece.typeId]?.name in outerUpperTypeNames
    }

    return listOf(
        OutfitBodyRow(
            title = "Headwear",
            layers = listOf(
                OutfitLayerSlot(
                    label = "Hat",
                    options = bySupertype[ClothingSupertype.HEADWEAR].orEmpty(),
                    generalSuggestion = if (weather.isRainy) "Rain hat / Cap" else "Cap / Hat",
                )
            ),
        ),
        OutfitBodyRow(
            title = "Upper body",
            layers = listOf(
                OutfitLayerSlot(
                    label = "Base layer",
                    options = baseUpper,
                    generalSuggestion = "T-Shirt / Shirt",
                ),
                OutfitLayerSlot(
                    label = "Outer layer",
                    options = outerUpper,
                    generalSuggestion = if (weather.isRainy) "Rain jacket" else "Jacket / Sweater",
                ),
            ),
        ),
        OutfitBodyRow(
            title = "Lower body",
            layers = listOf(
                OutfitLayerSlot(
                    label = "Bottoms",
                    options = bySupertype[ClothingSupertype.LOWER_BODY].orEmpty(),
                    generalSuggestion = if (weather.isRainy) "Long pants" else "Jeans / Pants",
                )
            ),
        ),
        OutfitBodyRow(
            title = "Footwear",
            layers = listOf(
                OutfitLayerSlot(
                    label = "Shoes",
                    options = bySupertype[ClothingSupertype.FOOTWEAR].orEmpty(),
                    generalSuggestion = if (weather.isRainy) "Waterproof shoes" else "Sneakers",
                )
            ),
        ),
    )
}
