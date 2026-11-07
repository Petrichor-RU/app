package nl.petrichor.app.ui.recommendation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import nl.petrichor.app.data.currentPlace
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.data.weather.WeatherRepository
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.wardrobe.ClothingCard
import kotlin.time.Clock

internal data class DayWeather(
    val isRainy: Boolean = false,
    val minTemperature: Int? = null,
    val maxTemperature: Int? = null,
)

internal data class OutfitLayerSlot(
    val label: String,
    val options: List<ClothingPiece>,
    val generalSuggestion: String,
)

internal data class OutfitBodyRow(
    val title: String,
    val layers: List<OutfitLayerSlot>,
) {
    constructor(title: String, slot: OutfitLayerSlot) : this(title, listOf(slot))
}

// TODO: Standardize outer outfit types somewhere else
private val outerUpperTypeNames = setOf("Jackets", "Hoodies", "Sweaters")

private class RecommendationController : ViewModel() {
    private val weatherRepository = WeatherRepository()
    private val wardrobeRepository = WardrobeRepository()
    val allClothing = wardrobeRepository.getAllClothing()
    val clothingTypes = wardrobeRepository.getClothingTypes()

    private val _locationName = MutableStateFlow("Finding your location...")
    val locationName = _locationName.asStateFlow()

    private val _weather = MutableStateFlow<WeatherSnapshot?>(null)
    val weather = _weather.asStateFlow()

    init {
        viewModelScope.launch {
            val place = currentPlace()
            _locationName.value = place.name
            weatherRepository.load(place).onSuccess { snapshot ->
                _weather.value = snapshot
                _locationName.value = snapshot.place.name
            }
        }
    }
}

@Composable
fun RecommendationScreen(
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        BoxMessage(message = "Recommendation preview", modifier = modifier.fillMaxSize())
        return
    }

    val controller = viewModel { RecommendationController() }
    val locationName by controller.locationName.collectAsStateWithLifecycle()
    val weather by controller.weather.collectAsStateWithLifecycle()
    val allClothing by controller.allClothing.collectAsStateWithLifecycle(emptyList())
    val clothingTypes by controller.clothingTypes.collectAsStateWithLifecycle(emptyList())

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var selectedDateIso by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDate = LocalDate.parse(selectedDateIso)

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

        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = {
                val exit = fadeOut(tween(130, easing = FastOutLinearInEasing)) +
                    scaleOut(tween(130, easing = FastOutLinearInEasing), targetScale = 0.97f)
                val enter = fadeIn(tween(280, delayMillis = 110, easing = LinearOutSlowInEasing)) +
                    scaleIn(tween(280, delayMillis = 110, easing = FastOutSlowInEasing), initialScale = 0.96f) +
                    slideInVertically(tween(280, delayMillis = 110, easing = FastOutSlowInEasing)) { it / 24 }
                enter togetherWith exit
            },
            label = "day_suggestions",
        ) { date ->
            val dayWeather = weather.dayWeatherFor(date, today)
            val bodyRows = remember(allClothing, clothingTypes, dayWeather) {
                recommendOutfitRows(allClothing, clothingTypes, dayWeather)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = suggestionsHeading(date, today),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                LocationNote(locationName = locationName, dayWeather = dayWeather)
                Spacer(Modifier.height(16.dp))

                bodyRows.forEachIndexed { index, row ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    OutfitBodyRowSection(row)
                }
            }
        }
    }
}

internal fun suggestionsHeading(selectedDate: LocalDate, today: LocalDate): String = when (selectedDate) {
    today -> "Suggestions for today"
    today.plus(1, DateTimeUnit.DAY) -> "Suggestions for tomorrow"
    else -> "Suggestions for ${selectedDate.formatted()}"
}

@Composable
private fun LocationNote(
    locationName: String,
    dayWeather: DayWeather,
) {
    val tempSuffix = if (dayWeather.maxTemperature != null && dayWeather.minTemperature != null) {
        " • ${dayWeather.maxTemperature}° / ${dayWeather.minTemperature}°"
    } else {
        ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbol(
            iconName = MaterialSymbols.LOCATION_ON,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Based on $locationName$tempSuffix",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OutfitBodyRowSection(row: OutfitBodyRow) {
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
                SwipeableClothingSlot(slot = slot, showLayerLabel = row.layers.size > 1)
            }
        }
    }
}

@Composable
private fun SwipeableClothingSlot(
    slot: OutfitLayerSlot,
    showLayerLabel: Boolean,
) {
    val optionCount = slot.options.size
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { optionCount.coerceAtLeast(1) })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(slot.options) {
        pagerState.scrollToPage(0)
    }

    fun stepOption(delta: Int) {
        if (optionCount > 1) {
            coroutineScope.launch {
                pagerState.animateScrollToPage((pagerState.currentPage + delta + optionCount) % optionCount)
            }
        }
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
            modifier = Modifier.size(132.dp).nestedScroll(consumeHorizontalScroll),
        ) { page ->
            val clothing = slot.options.getOrNull(page)
            if (clothing != null) {
                ClothingCard(
                    clothing = clothing,
                    onClick = { stepOption(1) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                GeneralRecommendationSlotCard(
                    suggestion = slot.generalSuggestion,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (optionCount > 1) {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { stepOption(-1) }, modifier = Modifier.size(32.dp)) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.CHEVRON_LEFT,
                        contentDescription = "Previous ${slot.label}",
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(optionCount.coerceAtMost(5)) { index ->
                        val isActive = index == pagerState.currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isActive) 12.dp else 5.dp,
                            label = "slot_dot_width",
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                            label = "slot_dot_color",
                        )
                        Box(
                            Modifier
                                .width(dotWidth)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
                IconButton(onClick = { stepOption(1) }, modifier = Modifier.size(32.dp)) {
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

private fun WeatherSnapshot.forecastDates(today: LocalDate): Set<LocalDate> =
    forecast.mapNotNull { runCatching { LocalDate.parse(it.label) }.getOrNull() }.toSet() + today

private fun WeatherSnapshot?.dayWeatherFor(date: LocalDate, today: LocalDate): DayWeather {
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

/**
 * Placeholder outfit builder in head-to-toe order (Headwear -> Upper body -> Lower body -> Footwear).
 * TODO: Replace with recommendation ranking logic based on weather.
 */
internal fun recommendOutfitRows(
    clothing: List<ClothingPiece>,
    clothingTypes: List<ClothingType>,
    weather: DayWeather,
): List<OutfitBodyRow> {
    val typeById = clothingTypes.associateBy { it.id }
    val bySupertype = clothing.groupBy { typeById[it.typeId]?.supertype }
    val (outerUpper, baseUpper) = bySupertype[ClothingSupertype.UPPER_BODY].orEmpty()
        .partition { typeById[it.typeId]?.name in outerUpperTypeNames }

    return listOf(
        OutfitBodyRow(
            title = "Headwear",
            slot = OutfitLayerSlot(
                label = "Hat",
                options = bySupertype[ClothingSupertype.HEADWEAR].orEmpty(),
                generalSuggestion = if (weather.isRainy) "Rain hat / Cap" else "Cap / Hat",
            ),
        ),
        OutfitBodyRow(
            title = "Upper body",
            layers = listOf(
                OutfitLayerSlot("Base layer", baseUpper, "T-Shirt / Shirt"),
                OutfitLayerSlot("Outer layer", outerUpper, if (weather.isRainy) "Rain jacket" else "Jacket / Sweater"),
            ),
        ),
        OutfitBodyRow(
            title = "Lower body",
            slot = OutfitLayerSlot(
                label = "Bottoms",
                options = bySupertype[ClothingSupertype.LOWER_BODY].orEmpty(),
                generalSuggestion = if (weather.isRainy) "Long pants" else "Jeans / Pants",
            ),
        ),
        OutfitBodyRow(
            title = "Footwear",
            slot = OutfitLayerSlot(
                label = "Shoes",
                options = bySupertype[ClothingSupertype.FOOTWEAR].orEmpty(),
                generalSuggestion = if (weather.isRainy) "Waterproof shoes" else "Sneakers",
            ),
        ),
    )
}
