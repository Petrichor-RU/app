package nl.petrichor.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.petrichor.app.data.currentPlace
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherRepository
import nl.petrichor.app.data.weather.WeatherSnapshot

private sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Ready(val weather: WeatherSnapshot) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

private class WeatherController : ViewModel() {
    private val repository = WeatherRepository()
    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    fun load(place: Place) {
        _state.value = WeatherUiState.Loading
        viewModelScope.launch {
            _state.value = repository.load(place).fold(
                onSuccess = { WeatherUiState.Ready(it) },
                onFailure = { WeatherUiState.Error(it.message ?: "Unable to load weather.") },
            )
        }
    }
}

@Composable
fun WeatherScreen(
    modifier: Modifier = Modifier,
) {
    val controller = viewModel { WeatherController() }
    val locationController = viewModel { LocationPickerController() }
    val state by controller.state.collectAsStateWithLifecycle()
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPlace) {
        val place = selectedPlace
        if (place == null) {
            selectedPlace = currentPlace()
        } else {
            controller.load(place)
        }
    }

    fun select(place: Place?) {
        selectedPlace = place
        pickerOpen = false
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        Column {
            Text(
                text = "Location",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { pickerOpen = !pickerOpen }) {
                Text(
                    text = selectedPlace?.name ?: "Finding your location...",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.width(8.dp))
                MaterialSymbol(
                    iconName = if (pickerOpen) MaterialSymbols.KEYBOARD_ARROW_DOWN else MaterialSymbols.KEYBOARD_ARROW_UP,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (pickerOpen) {
            LocationPicker(
                onLocationSelected = ::select,
                onCurrentLocationSelected = { select(null) },
                controller = locationController
            )
        } else {
            val currentState = state
            when {
                selectedPlace == null || currentState is WeatherUiState.Loading -> Column(
                    Modifier.fillMaxWidth().padding(top = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Updating forecast...")
                }
                currentState is WeatherUiState.Error -> BoxMessage(currentState.message, Modifier.fillMaxWidth().padding(top = 28.dp))
                currentState is WeatherUiState.Ready -> WeatherContent(currentState.weather)
            }
        }
    }
}

@Composable
private fun WeatherContent(weather: WeatherSnapshot) {
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
