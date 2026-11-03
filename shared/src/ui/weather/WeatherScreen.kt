package nl.petrichor.app.ui.weather

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.petrichor.app.data.currentPlace
import nl.petrichor.app.data.location.SelectedLocationRepository
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherRepository
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.platform.BackHandler
import nl.petrichor.app.ui.BoxMessage

sealed interface ForecastState {
    data object Loading : ForecastState
    data class Ready(val weather: WeatherSnapshot) : ForecastState
    data class Error(val message: String) : ForecastState
}

data class WeatherUiState(
    val place: Place? = null,
    val forecast: ForecastState = ForecastState.Loading,
)

class WeatherViewModel(
    private val weatherRepository: WeatherRepository = WeatherRepository(),
    private val selectedLocationRepository: SelectedLocationRepository = SelectedLocationRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(WeatherUiState())
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            selectedLocationRepository.selected.collectLatest { selected ->
                _state.value = WeatherUiState(place = selected)
                val place = selected ?: currentPlace()
                _state.update { it.copy(place = place) }
                val result = weatherRepository.load(place)
                _state.update {
                    it.copy(
                        forecast = result.fold(
                            onSuccess = { weather -> ForecastState.Ready(weather) },
                            onFailure = { error -> ForecastState.Error(error.message ?: "Unable to load weather.") },
                        ),
                    )
                }
            }
        }
    }

    fun select(place: Place?) {
        viewModelScope.launch { selectedLocationRepository.select(place) }
    }
}

@Composable
fun WeatherScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel { WeatherViewModel() }
    val pickerViewModel = viewModel { LocationPickerViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }

    fun closePicker() {
        pickerOpen = false
        pickerViewModel.search("")
    }

    fun select(place: Place?) {
        viewModel.select(place)
        closePicker()
    }

    BackHandler(enabled = pickerOpen, onBack = ::closePicker)

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        LocationHeader(
            locationName = state.place?.name ?: "Finding your location...",
            pickerOpen = pickerOpen,
            onTogglePicker = {
                if (pickerOpen) closePicker() else pickerOpen = true
            }
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxSize()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !pickerOpen,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { height -> height / 10 },
                exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                    slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { height -> height / 10 },
            ) {
                ForecastBody(state.forecast)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = pickerOpen,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { height -> -height / 10 },
                exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                    slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { height -> -height / 10 },
            ) {
                LocationPicker(
                    onLocationSelected = ::select,
                    onCurrentLocationSelected = { select(null) },
                    viewModel = pickerViewModel
                )
            }
        }
    }
}

@Composable
private fun ForecastBody(forecast: ForecastState) {
    AnimatedContent(
        targetState = forecast,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(durationMillis = 220, delayMillis = 90)).togetherWith(fadeOut(tween(90))) },
        label = "forecast_state",
    ) { current ->
        when (current) {
            ForecastState.Loading -> Column(
                Modifier.fillMaxWidth().padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Updating forecast...")
            }
            is ForecastState.Error -> BoxMessage(current.message, Modifier.fillMaxWidth().padding(top = 28.dp))
            is ForecastState.Ready -> WeatherContent(current.weather)
        }
    }
}
