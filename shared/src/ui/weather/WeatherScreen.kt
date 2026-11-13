package nl.petrichor.app.ui.weather

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.petrichor.app.data.location.SelectedLocationStore
import nl.petrichor.app.data.location.currentPlace
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherRepository
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.platform.BackHandler
import nl.petrichor.app.ui.BoxMessage

private sealed interface WeatherUiState {
    data object Loading : WeatherUiState

    data class Ready(
        val weather: WeatherSnapshot,
    ) : WeatherUiState

    data class Error(
        val message: String,
    ) : WeatherUiState
}

private class WeatherController : ViewModel() {
    private val repository = WeatherRepository()
    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    fun load(place: Place) {
        _state.value = WeatherUiState.Loading
        viewModelScope.launch {
            _state.value =
                repository.load(place).fold(
                    onSuccess = { WeatherUiState.Ready(it) },
                    onFailure = { WeatherUiState.Error(it.message ?: "Unable to load weather.") },
                )
        }
    }
}

@Composable
@Suppress("LongMethod") // screen-sized composable owned by the weather tab
fun WeatherScreen(modifier: Modifier = Modifier) {
    val controller = viewModel { WeatherController() }
    val locationController = viewModel { LocationPickerController() }
    val locationStore = remember { SelectedLocationStore() }
    val state by controller.state.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    // Shared selected place (decision 19): null means "use current location".
    val selectedPlace by locationStore.selectedPlace.collectAsStateWithLifecycle(null)
    var resolvedCurrentPlace by remember { mutableStateOf<Place?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPlace) {
        val place = selectedPlace
        if (place == null) {
            // Only resolve the current location when no shared selection exists.
            val current = currentPlace()
            resolvedCurrentPlace = current
            controller.load(current)
        } else {
            controller.load(place)
        }
    }

    fun closePicker() {
        pickerOpen = false
        locationController.search("")
    }

    fun select(place: Place?) {
        coroutineScope.launch {
            if (place == null) locationStore.clear() else locationStore.select(place)
        }
        closePicker()
    }

    BackHandler(enabled = pickerOpen, onBack = ::closePicker)

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        LocationHeader(
            locationName = selectedPlace?.name ?: resolvedCurrentPlace?.name ?: "Finding your location...",
            pickerOpen = pickerOpen,
            onTogglePicker = {
                if (pickerOpen) closePicker() else pickerOpen = true
            },
        )
        Spacer(Modifier.height(16.dp))
        WeatherScreenBody(
            pickerOpen = pickerOpen,
            state = state,
            locationController = locationController,
            onLocationSelected = ::select,
            onCurrentLocationSelected = { select(null) },
        )
    }
}

@Composable
@Suppress("LongParameterList") // body inputs are passed individually; grouping them would obscure the call site
private fun WeatherScreenBody(
    pickerOpen: Boolean,
    state: WeatherUiState,
    locationController: LocationPickerController,
    onLocationSelected: (Place) -> Unit,
    onCurrentLocationSelected: () -> Unit,
) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        AnimatedVisibility(
            visible = !pickerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ForecastBody(state)
        }
        AnimatedVisibility(
            visible = pickerOpen,
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut() + slideOutVertically { it / 12 },
        ) {
            LocationPicker(
                onLocationSelected = onLocationSelected,
                onCurrentLocationSelected = onCurrentLocationSelected,
                controller = locationController,
            )
        }
    }
}

@Composable
private fun ForecastBody(state: WeatherUiState) {
    AnimatedContent(targetState = state, contentKey = { it::class }) { current ->
        when (current) {
            is WeatherUiState.Loading -> {
                Column(
                    Modifier.fillMaxWidth().padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Updating forecast...")
                }
            }

            is WeatherUiState.Error -> {
                BoxMessage(current.message, Modifier.fillMaxWidth().padding(top = 28.dp))
            }

            is WeatherUiState.Ready -> {
                WeatherContent(current.weather)
            }
        }
    }
}

@Composable
internal fun WeatherContent(
    weather: WeatherSnapshot,
    modifier: Modifier = Modifier,
) {
    // Root must be a single node: callers may place this inside Box-scoped containers
    // (e.g. AnimatedContent), where multiple roots would overlap at TopStart.
    Column(modifier) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text(weather.description, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${weather.temperature}°", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text("Feels like ${weather.feelsLike}°", modifier = Modifier.padding(bottom = 10.dp))
                }
                Text("Humidity ${weather.humidity}%  |  Wind ${weather.windSpeed} km/h", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("5-day forecast", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(weather.forecast) { day ->
                Card(Modifier.width(108.dp)) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.label.substringAfter("-"), fontWeight = FontWeight.SemiBold)
                        Text("${day.max}°", style = MaterialTheme.typography.titleLarge)
                        Text("${day.min}°", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
