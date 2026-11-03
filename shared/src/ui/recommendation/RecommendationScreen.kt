package nl.petrichor.app.ui.recommendation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
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

/** Weather prediction for a single calendar day, used as input for the outfit recommendation. */
internal data class DayWeather(
    val date: LocalDate,
    val minTemperature: Int,
    val maxTemperature: Int,
    val description: String? = null,
)

private class RecommendationViewModel : ViewModel() {
    private val weatherRepository = WeatherRepository()
    private val wardrobeRepository = WardrobeRepository()
    val allClothing = wardrobeRepository.getAllClothing()
    val clothingTypes = wardrobeRepository.getClothingTypes()

    private val _weather = MutableStateFlow<RecommendationWeatherState>(RecommendationWeatherState.Loading)
    val weather: StateFlow<RecommendationWeatherState> = _weather.asStateFlow()

    fun loadWeather(place: Place) {
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
    val weatherState by viewModel.weather.collectAsStateWithLifecycle()
    val allClothing by viewModel.allClothing.collectAsStateWithLifecycle(emptyList())
    val clothingTypes by viewModel.clothingTypes.collectAsStateWithLifecycle(emptyList())
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    // Stored as an ISO string so the selection survives switching tabs
    var selectedDateIso by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDate = LocalDate.parse(selectedDateIso)

    LaunchedEffect(Unit) {
        viewModel.loadWeather(currentPlace())
    }

    val currentWeatherState = weatherState
    val weather = (currentWeatherState as? RecommendationWeatherState.Ready)?.weather
    val dayWeather = weather?.dayWeatherFor(selectedDate, today)
    val outfit = dayWeather?.let { recommendOutfit(allClothing, clothingTypes, it) }.orEmpty()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            RecommendationCalendar(
                selectedDate = selectedDate,
                today = today,
                onDateSelected = { date -> selectedDateIso = date.toString() },
                forecastDates = weather?.forecastDates(today).orEmpty(),
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    if (selectedDate == today) "Recommended for today" else "Recommended for ${selectedDate.formatted()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                when (currentWeatherState) {
                    RecommendationWeatherState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.width(20.dp).aspectRatio(1f), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Loading forecast...")
                    }
                    is RecommendationWeatherState.Error -> BoxMessage(currentWeatherState.message)
                    is RecommendationWeatherState.Ready -> if (dayWeather != null) {
                        DayWeatherCard(dayWeather)
                    } else {
                        BoxMessage("No weather forecast available for this day yet.")
                    }
                }
            }
        }

        when {
            dayWeather == null -> Unit
            allClothing.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                BoxMessage("Add clothing to your wardrobe to get outfit recommendations.")
            }
            outfit.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                BoxMessage("No outfit could be put together for this day.")
            }
            else -> items(outfit, key = { it.id }) { clothing ->
                ClothingCard(
                    clothing = clothing,
                    onClick = {},
                    modifier = Modifier.aspectRatio(1f)
                )
            }
        }
    }
}

@Composable
private fun DayWeatherCard(dayWeather: DayWeather) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(iconName = MaterialSymbols.THERMOSTAT, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "${dayWeather.maxTemperature}° / ${dayWeather.minTemperature}°",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                dayWeather.description?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Dates covered by the forecast, today included since current weather is always known. */
private fun WeatherSnapshot.forecastDates(today: LocalDate): Set<LocalDate> =
    forecast.mapNotNull { runCatching { LocalDate.parse(it.label) }.getOrNull() }.toSet() + today

/** Weather prediction for [date], or null when the forecast does not reach that day. */
private fun WeatherSnapshot.dayWeatherFor(date: LocalDate, today: LocalDate): DayWeather? {
    val forecastDay = forecast.firstOrNull { it.label == date.toString() }
    return when {
        date == today -> DayWeather(
            date = date,
            minTemperature = minOf(forecastDay?.min ?: temperature, temperature),
            maxTemperature = maxOf(forecastDay?.max ?: temperature, temperature),
            description = description,
        )
        forecastDay != null -> DayWeather(date, forecastDay.min, forecastDay.max)
        else -> null
    }
}

/**
 * Placeholder outfit picker: takes the newest item of each body part from the wardrobe.
 * TODO: Replace with recommendation logic based on [weather].
 */
@Suppress("UNUSED_PARAMETER")
internal fun recommendOutfit(
    clothing: List<ClothingPiece>,
    clothingTypes: List<ClothingType>,
    weather: DayWeather,
): List<ClothingPiece> {
    val supertypeById = clothingTypes.associate { it.id to it.supertype }
    val bySupertype = clothing.groupBy { supertypeById[it.typeId] }
    val hasSeparates = ClothingSupertype.UPPER_BODY in bySupertype && ClothingSupertype.LOWER_BODY in bySupertype
    return ClothingSupertype.entries
        .filterNot { it == ClothingSupertype.FULL_BODY && hasSeparates }
        .mapNotNull { supertype -> bySupertype[supertype]?.firstOrNull() }
}
