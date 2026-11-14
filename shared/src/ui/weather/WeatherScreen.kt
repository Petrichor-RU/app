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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import nl.petrichor.app.data.location.SelectedLocationStore
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.platform.BackHandler
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.background.AppWeatherState

/**
 * Weather tab: shows the current weather for the picked location. Consumes the app-shell-shared
 * [AppWeatherState] (loaded once per selected place by AppBackgroundController in App.kt) instead
 * of issuing its own request; only the location picker state is local to this screen.
 */
@Composable
@Suppress("LongMethod") // screen-sized composable owned by the weather tab
fun WeatherScreen(
    weatherState: AppWeatherState,
    modifier: Modifier = Modifier,
) {
    val locationController = viewModel { LocationPickerController() }
    val locationStore = remember { SelectedLocationStore() }
    val coroutineScope = rememberCoroutineScope()
    var pickerOpen by remember { mutableStateOf(false) }

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
            locationName = weatherState.place?.name ?: "Finding your location...",
            pickerOpen = pickerOpen,
            onTogglePicker = {
                if (pickerOpen) closePicker() else pickerOpen = true
            },
        )
        Spacer(Modifier.height(16.dp))
        WeatherScreenBody(
            pickerOpen = pickerOpen,
            state = weatherState,
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
    state: AppWeatherState,
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

/** Switches between the loading spinner, the error message and [WeatherContent] as the shared state settles. */
@Composable
private fun ForecastBody(state: AppWeatherState) {
    AnimatedContent(targetState = state, contentKey = { it.loading to it.error }) { current ->
        when {
            current.loading -> {
                Column(Modifier.fillMaxWidth().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Updating forecast...")
                }
            }

            current.error != null -> {
                BoxMessage(current.error, Modifier.fillMaxWidth().padding(top = 28.dp))
            }

            current.weather != null -> {
                WeatherContent(current.weather)
            }
        }
    }
}

/**
 * Current-weather card plus the 5-day forecast row. Width is capped at the forecast row's width
 * so the tab stays readable on tablets and desktop. Root must be a single node: callers may place
 * this inside Box-scoped containers (e.g. AnimatedContent), where multiple roots would overlap
 * at TopStart.
 */
@Composable
internal fun WeatherContent(
    weather: WeatherSnapshot,
    modifier: Modifier = Modifier,
) {
    val forecastCardWidth = 108.dp
    val forecastSpacing = 10.dp
    val forecastWidth = forecastCardWidth * 5 + forecastSpacing * 4
    Column(modifier.widthIn(max = forecastWidth)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)),
        ) {
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(forecastSpacing)) {
            items(weather.forecast) { day ->
                Card(
                    Modifier.width(forecastCardWidth),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
                ) {
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
