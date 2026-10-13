package nl.petrichor.app.data.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val searchDebounce = 350.milliseconds
private const val minQueryLength = 2

sealed interface LocationSearchState {
    data object Idle : LocationSearchState
    data object Loading : LocationSearchState
    data class Ready(val results: List<LocationResult>) : LocationSearchState
    data class Error(val message: String) : LocationSearchState
}

data class LocationPickerUiState(
    val query: String = "",
    val search: LocationSearchState = LocationSearchState.Idle,
    val saved: List<LocationResult> = emptyList(),
)

class LocationPickerController(
    private val searchRepository: LocationSearchRepository = LocationSearchRepository(),
    private val savedRepository: SavedLocationsRepository = SavedLocationsRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(LocationPickerUiState())
    val state: StateFlow<LocationPickerUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            savedRepository.locations.collect { saved -> _state.update { it.copy(saved = saved) } }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < minQueryLength) {
            _state.update { it.copy(search = LocationSearchState.Idle) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(searchDebounce)
            _state.update { it.copy(search = LocationSearchState.Loading) }
            val result = searchRepository.search(query.trim())
            ensureActive()
            _state.update {
                it.copy(
                    search = result.fold(
                        onSuccess = { results -> LocationSearchState.Ready(results) },
                        onFailure = { error -> LocationSearchState.Error(error.message ?: "Unable to search locations.") },
                    ),
                )
            }
        }
    }

    fun add(location: LocationResult) {
        viewModelScope.launch { savedRepository.add(location) }
        search("")
    }

    fun remove(location: LocationResult) {
        viewModelScope.launch { savedRepository.remove(location) }
    }

    fun move(fromIndex: Int, toIndex: Int) {
        _state.update { s ->
            if (fromIndex !in s.saved.indices || toIndex !in s.saved.indices) return@update s
            s.copy(saved = s.saved.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
        }
    }

    fun commitOrder() {
        val order = _state.value.saved
        viewModelScope.launch { savedRepository.update { order } }
    }

}