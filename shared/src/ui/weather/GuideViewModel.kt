package nl.petrichor.app.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.petrichor.app.data.guide.ClimateProfile
import nl.petrichor.app.data.guide.GuideRepository
import nl.petrichor.app.data.weather.Place

/**
 * App-shell-scoped state for the location guide, mirroring how [nl.petrichor.app.ui.background.AppBackgroundController]
 * holds the weather: because it lives in a [ViewModel] owned by App.kt (not in the pager page), the profile,
 * the in-flight fetch and the expansion flag survive the Weather page being disposed and recomposed on tab
 * switches, on the location picker opening, and on the forecast's loading spinner swapping the content, and a
 * fetch is never cancelled together with the page.
 *
 * @param loadClimate the single climate request; injected for tests.
 */
class GuideViewModel(
    private val loadClimate: suspend (Place, Int) -> ClimateProfile? = { place, humidity -> GuideRepository().climateFor(place, humidity) },
) : ViewModel() {
    private val mutableProfile = MutableStateFlow<ClimateProfile?>(null)

    /**
     * Climate profile of the last successfully loaded place; kept while a new place loads, but
     * cleared when that fetch fails so the guide shows its not-available message.
     */
    val profile: StateFlow<ClimateProfile?> = mutableProfile.asStateFlow()
    private val mutableLoading = MutableStateFlow(false)

    /** True while a place's climate is being fetched (the guide header shows a spinner). */
    val loading: StateFlow<Boolean> = mutableLoading.asStateFlow()
    private val mutableExpanded = MutableStateFlow(false)

    /** Whether the guide's season cards are visible; app-shell-scoped so it survives every recomposition. */
    val expanded: StateFlow<Boolean> = mutableExpanded.asStateFlow()

    /** The place whose fetch is currently in flight, if any. */
    private var inFlightPlace: Place? = null

    fun toggleExpanded() {
        mutableExpanded.value = !mutableExpanded.value
    }

    /**
     * Ensures the climate for [place] is loaded: fetches it unless it is already the current
     * [profile] or a fetch for it is in flight. While a new place loads, the previous profile
     * stays on screen; a null result clears the profile so the expanded guide shows its
     * "no climate data available" message instead of a stale chart. If a newer place is requested
     * while a fetch is in flight, that stale result is dropped and only the latest request
     * publishes and clears the [loading] flag — so a failed fetch never blocks retries and never
     * leaves the spinner running.
     */
    fun ensureClimateLoaded(
        place: Place,
        humidity: Int,
    ) {
        if (mutableProfile.value?.place == place || inFlightPlace == place) return
        inFlightPlace = place
        viewModelScope.launch {
            mutableLoading.value = true
            try {
                val result = loadClimate(place, humidity)
                // A null result means the fetch failed: clear any stale profile so the guide says
                // the data is unavailable rather than showing another place's chart.
                if (inFlightPlace == place) mutableProfile.value = result
            } finally {
                // Only the latest request may clear the loading flag; a cancelled fetch must not
                // leave the guide spinning forever either.
                if (inFlightPlace == place) {
                    inFlightPlace = null
                    mutableLoading.value = false
                }
            }
        }
    }
}
