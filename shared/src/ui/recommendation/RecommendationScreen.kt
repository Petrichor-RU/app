package nl.petrichor.app.ui.recommendation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kizitonwose.calendar.core.now
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.currentPlace
import nl.petrichor.app.data.recommendation.dayWeatherFor
import nl.petrichor.app.data.recommendation.forecastDates
import nl.petrichor.app.data.recommendation.recommendOutfit
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.data.weather.WeatherRepository
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.ui.BoxMessage

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

    val today = remember { LocalDate.now() }
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
            val outfit = remember(allClothing, clothingTypes, dayWeather) {
                recommendOutfit(allClothing, clothingTypes, dayWeather)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LocationNote(locationName = locationName, dayWeather = dayWeather)
                Spacer(Modifier.height(16.dp))

                OutfitBodyRowSection("Headwear", outfit.headwear)
                Spacer(Modifier.height(14.dp))
                OutfitBodyRowSection("Upper body", outfit.baseUpper, outfit.outerUpper)
                Spacer(Modifier.height(14.dp))
                OutfitBodyRowSection("Lower body", outfit.lowerBody)
                Spacer(Modifier.height(14.dp))
                OutfitBodyRowSection("Footwear", outfit.footwear)
            }
        }
    }
}
