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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import nl.petrichor.app.data.guide.ClimateProfile
import nl.petrichor.app.data.location.SelectedLocationStore
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.platform.BackHandler
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.background.AppWeatherState

private val forecastCardWidth = 108.dp
private val forecastSpacing = 10.dp
private val forecastWidth = forecastCardWidth * 5 + forecastSpacing * 4

/**
 * Weather tab: shows the current weather for the picked location. Consumes the app-shell-shared
 * [AppWeatherState] (loaded once per selected place by AppBackgroundController in App.kt) instead
 * of issuing its own request; only the location picker state is local to this screen.
 */
@Composable
@Suppress("LongMethod") // screen-sized composable owned by the weather tab
fun WeatherScreen(
    weatherState: AppWeatherState,
    guideViewModel: GuideViewModel? = null,
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
            guideViewModel = guideViewModel,
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
    guideViewModel: GuideViewModel?,
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
            ForecastBody(state, guideViewModel)
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

/**
 * Scrollable forecast area — loading spinner, error message or [WeatherContent] as the shared
 * state settles — with the location guide below it. The guide sits deliberately outside the
 * [AnimatedContent] swap: the section is always visible (no switches on its visibility) and keeps
 * updating with the forecast while a new place loads. Width is capped at the forecast row's width
 * so the tab stays readable on tablets and desktop, and the column scrolls vertically so the
 * season cards stay reachable on short windows.
 */
@Composable
internal fun ForecastBody(
    state: AppWeatherState,
    guideViewModel: GuideViewModel?,
) {
    // Guide state lives in the app-shell [GuideViewModel] (like the forecast in
    // AppBackgroundController), so the profile, the in-flight fetch and the expansion flag survive
    // the pager disposing this page on tab switches and the location picker opening. When the
    // place changes, the previous profile stays on screen while the new one loads (spinner in the
    // header), then swaps in.
    val guide = rememberGuideState(guideViewModel)
    val weather = state.weather
    LaunchedEffect(weather?.place) {
        if (weather != null) guideViewModel?.ensureClimateLoaded(weather.place, weather.humidity)
    }
    Column(Modifier.widthIn(max = forecastWidth).verticalScroll(rememberScrollState())) {
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
        Spacer(Modifier.height(24.dp))
        LocationGuide(
            profile = guide.profile,
            loading = guide.loading,
            expanded = guide.expanded,
            onToggleExpanded = guide.onToggleExpanded,
        )
    }
}

/**
 * Current-weather card plus the 5-day forecast row. Root must be a single node: callers may place
 * this inside Box-scoped containers (e.g. AnimatedContent), where multiple roots would overlap at
 * TopStart.
 */
@Composable
internal fun WeatherContent(
    weather: WeatherSnapshot,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
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

/** Guide inputs collected from the app-shell [GuideViewModel]; falls back to local state without one (tests). */
private data class GuideState(
    val profile: ClimateProfile?,
    val loading: Boolean,
    val expanded: Boolean,
    val onToggleExpanded: () -> Unit,
)

@Composable
private fun rememberGuideState(guideViewModel: GuideViewModel?): GuideState {
    var localExpanded by rememberSaveable { mutableStateOf(false) }
    return if (guideViewModel != null) {
        GuideState(
            profile = guideViewModel.profile.collectAsStateWithLifecycle().value,
            loading = guideViewModel.loading.collectAsStateWithLifecycle().value,
            expanded = guideViewModel.expanded.collectAsStateWithLifecycle().value,
            onToggleExpanded = guideViewModel::toggleExpanded,
        )
    } else {
        GuideState(profile = null, loading = false, expanded = localExpanded) { localExpanded = !localExpanded }
    }
}
