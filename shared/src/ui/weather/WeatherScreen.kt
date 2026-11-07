package nl.petrichor.app.ui.weather

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nl.petrichor.app.data.currentPlace
import nl.petrichor.app.data.location.SelectedLocationRepository
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherRepository
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.platform.BackHandler
import nl.petrichor.app.ui.BoxMessage

private sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Ready(val weather: WeatherSnapshot) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

private class WeatherViewModel(
    private val weatherRepository: WeatherRepository = WeatherRepository(),
    private val selectedLocationRepository: SelectedLocationRepository = SelectedLocationRepository(),
) : ViewModel() {
    var selectedPlace by mutableStateOf<Place?>(null)
        private set
    var state by mutableStateOf<WeatherUiState>(WeatherUiState.Loading)
        private set

    init {
        viewModelScope.launch {
            selectedLocationRepository.selected.collectLatest { saved ->
                selectedPlace = saved
                state = WeatherUiState.Loading
                val place = saved ?: currentPlace()
                selectedPlace = place
                state = weatherRepository.load(place).fold(
                    onSuccess = { WeatherUiState.Ready(it) },
                    onFailure = { WeatherUiState.Error(it.message ?: "Unable to load weather.") },
                )
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
            locationName = viewModel.selectedPlace?.name ?: "Finding your location...",
            pickerOpen = pickerOpen,
            onTogglePicker = {
                if (pickerOpen) closePicker() else pickerOpen = true
            }
        )
        Spacer(Modifier.height(16.dp))
        WeatherScreenBody(
            pickerOpen = pickerOpen,
            state = viewModel.state,
            pickerViewModel = pickerViewModel,
            onLocationSelected = ::select,
            onCurrentLocationSelected = { select(null) },
        )
    }
}

@Composable
private fun WeatherScreenBody(
    pickerOpen: Boolean,
    state: WeatherUiState,
    pickerViewModel: LocationPickerViewModel,
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
                viewModel = pickerViewModel,
            )
        }
    }
}

@Composable
private fun ForecastBody(state: WeatherUiState) {
    AnimatedContent(targetState = state, contentKey = { it::class }) { current ->
        when (current) {
            WeatherUiState.Loading -> Column(
                Modifier.fillMaxWidth().padding(top = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Updating forecast...")
            }
            is WeatherUiState.Error -> BoxMessage(current.message, Modifier.fillMaxWidth().padding(top = 28.dp))
            is WeatherUiState.Ready -> WeatherContent(current.weather)
        }
    }
}
