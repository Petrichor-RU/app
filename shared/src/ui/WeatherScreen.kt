package nl.petrichor.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.petrichor.app.data.currentPlace
import nl.petrichor.app.data.weather.Coordinates
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

private val presetPlaces = listOf(
    Place("Amsterdam", Coordinates(52.3676, 4.9041)),
    Place("London", Coordinates(51.5072, -0.1276)),
    Place("New York", Coordinates(40.7128, -74.0060)),
    Place("Tokyo", Coordinates(35.6762, 139.6503)),
)

@Composable
fun WeatherScreen(
    modifier: Modifier = Modifier,
) {
    val controller = remember { WeatherController() }
    val state by controller.state.collectAsStateWithLifecycle()
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val location = currentPlace()
        selectedPlace = location
        controller.load(location)
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        Column {
            Text("Location", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { menuExpanded = true }) {
                Text(selectedPlace?.name ?: "Finding your location...", style = MaterialTheme.typography.titleLarge)
                Text("  v", color = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                presetPlaces.forEach { place ->
                    DropdownMenuItem(text = { Text(place.name) }, onClick = {
                        selectedPlace = place
                        menuExpanded = false
                        controller.load(place)
                    })
                }
                DropdownMenuItem(text = { Text("Use current location") }, onClick = {
                    val place = Place("Current location", Coordinates(52.3676, 4.9041))
                    selectedPlace = place
                    menuExpanded = false
                    controller.load(place)
                })
            }
        }
        val currentState = state
        when (currentState) {
            WeatherUiState.Loading -> Column(Modifier.fillMaxWidth().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Updating forecast...")
            }
            is WeatherUiState.Error -> BoxMessage(currentState.message, Modifier.fillMaxWidth().padding(top = 28.dp))
            is WeatherUiState.Ready -> WeatherContent(currentState.weather)
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
