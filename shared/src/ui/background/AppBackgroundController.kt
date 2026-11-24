package nl.petrichor.app.ui.background

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import nl.petrichor.app.data.background.BackgroundCondition
import nl.petrichor.app.data.background.BackgroundConditionResolver
import nl.petrichor.app.data.location.SelectedLocationStore
import nl.petrichor.app.data.location.currentPlace
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherRepository
import nl.petrichor.app.data.weather.WeatherSnapshot
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Shell-owned weather state: the single load for the resolved place, created by
 * [AppBackgroundController] in App.kt and shared by the background scene, the Weather tab and
 * the Suggestions tab so they never issue duplicate requests.
 *
 * @param place the selected place, or the device's current location when nothing is selected.
 * @param weather the loaded snapshot; null while [loading] or after a failure.
 * @param loading true from a (re)selection until the request settles.
 * @param error failure message of the last request, if any.
 * @param loadedAt when the successful load completed; the freshness source for
 *   BackgroundConditionResolver (stale weather keeps the location offset but loses its mood).
 */
data class AppWeatherState(
    val place: Place? = null,
    val weather: WeatherSnapshot? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val loadedAt: Instant? = null,
)

/**
 * App-shell view model bridging the data layer and App.kt: it owns the one weather request per
 * selected place ([selectedPlaces], defaulting to the current location via [resolvePlace]) and
 * derives the background [BackgroundCondition] from the result plus a 60 s ticker.
 *
 * Call [run] inside `repeatOnLifecycle(STARTED)`: location work and the ticker stop while the
 * app is hidden, a completed load is preserved on return, and an interrupted load restarts.
 *
 * @param selectedPlaces shared selected-place stream (null means "use current location").
 * @param loadWeather the single weather request; invoked once per selection change.
 * @param resolvePlace resolves the device's current location when nothing is selected.
 * @param now clock, injectable for tests.
 */
class AppBackgroundController(
    private val selectedPlaces: Flow<Place?> = SelectedLocationStore().selectedPlace,
    private val loadWeather: suspend (Place) -> Result<WeatherSnapshot> = WeatherRepository()::load,
    private val resolvePlace: suspend () -> Place = ::currentPlace,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {
    private val mutableWeather = MutableStateFlow(AppWeatherState())

    /** Shared weather state, handed to the Weather and Suggestions tabs by App.kt. */
    val weather = mutableWeather.asStateFlow()
    private val mutableCondition = MutableStateFlow<BackgroundCondition?>(null)

    /** Current background condition; null only before the first resolve. Recomputed every 60 s and after each load. */
    val condition = mutableCondition.asStateFlow()
    private var selected: Place? = null
    private var hasSelection = false

    /**
     * Collection loop for the app shell: recomputes [condition] every 60 s (so time-band rollovers
     * crossfade live) and reloads [weather] whenever the selected place changes or a previous
     * load was interrupted. Runs forever; cancel it via the surrounding lifecycle scope.
     */
    suspend fun run() =
        coroutineScope {
            launch {
                while (true) {
                    resolve()
                    delay(60_000.milliseconds)
                }
            }
            selectedPlaces.distinctUntilChanged().collectLatest { selection ->
                if (!hasSelection || selected != selection || mutableWeather.value.loading) {
                    selected = selection
                    hasSelection = true
                    mutableWeather.value = AppWeatherState()
                    resolve()
                    val place = selection ?: resolvePlace()
                    mutableWeather.value = AppWeatherState(place = place)
                    val result = loadWeather(place)
                    mutableWeather.value =
                        result.fold(
                            onSuccess = { AppWeatherState(place, it, loading = false, loadedAt = now()) },
                            onFailure = { AppWeatherState(place, loading = false, error = it.message ?: "Unable to load weather.") },
                        )
                    resolve()
                }
            }
        }

    private fun resolve() {
        val state = mutableWeather.value
        mutableCondition.value = BackgroundConditionResolver.resolve(state.weather, now(), state.loadedAt)
    }
}
