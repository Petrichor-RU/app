package nl.petrichor.app.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import nl.petrichor.app.data.guide.ClimateProfile
import nl.petrichor.app.data.guide.MonthClimate
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.ForecastDay
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.ui.background.AppBackgroundController
import kotlin.test.Test
import kotlin.time.Clock

/**
 * Regression tests for the location guide following a newly picked location. The guide is rendered
 * through the same ForecastBody-style [AnimatedContent] (the loading spinner swaps the whole content,
 * keyed on loading/error) and the picker's hiding [AnimatedVisibility] as the real WeatherScreen,
 * driven by a real [AppBackgroundController].
 */
class GuidePlaceSwitchTest {
    private val amsterdam = Place("Amsterdam", Coordinates(52.37, 4.89))
    private val helsinki = Place("Helsinki", Coordinates(60.17, 24.94))

    private fun snapshot(place: Place) =
        WeatherSnapshot(
            place = place,
            temperature = 10,
            feelsLike = 8,
            description = "Cloudy",
            icon = "",
            conditionId = 804,
            humidity = 80,
            windSpeed = 12,
            forecast = List(5) { ForecastDay("2026-11-0${it + 1}", 5 + it, 12 + it, "", 804) },
        )

    private fun profile(
        place: Place,
        high: Double,
        low: Double,
    ) = ClimateProfile(place, List(12) { MonthClimate(it + 1, high, low, 30.0, 50) })

    private fun amsterdamProfile() = profile(amsterdam, high = 23.0, low = 7.0)

    private fun helsinkiProfile() = profile(helsinki, high = -5.0, low = -12.0)

    /** Mirrors WeatherScreenBody: the picker hides the forecast body (which hosts the guide). */
    @Composable
    private fun Harness(
        controller: AppBackgroundController,
        viewModel: GuideViewModel,
        pickerOpen: Boolean,
    ) {
        LaunchedEffect(Unit) { controller.run() }
        val state = controller.weather.collectAsStateWithLifecycle().value
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = !pickerOpen, enter = fadeIn(), exit = fadeOut()) {
                ForecastBody(state, viewModel)
            }
            AnimatedVisibility(visible = pickerOpen, enter = fadeIn(), exit = fadeOut()) {
                Text("Picker")
            }
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `guide section stays visible while the forecast reloads for the new place`() =
        runDesktopComposeUiTest(width = 800, height = 1400) {
            val selections = MutableStateFlow<Place?>(amsterdam)
            val pending = mutableMapOf<Place, CompletableDeferred<WeatherSnapshot>>()
            val controller = weatherController(selections, pending)
            val viewModel = GuideViewModel { place, _ -> if (place == amsterdam) amsterdamProfile() else helsinkiProfile() }
            var pickerOpen by mutableStateOf(false)
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        val holder = rememberSaveableStateHolder()
                        holder.SaveableStateProvider("weather-page") {
                            Harness(controller, viewModel, pickerOpen)
                        }
                    }
                }
            }
            waitForIdle()
            pending.remove(amsterdam)?.complete(snapshot(amsterdam))
            waitForIdle()

            onNodeWithText("Location guide").performClick()
            waitForIdle()
            onNodeWithText("23°").assertIsDisplayed()

            // Pick Helsinki: the forecast swaps to its loading spinner, the guide must not.
            pickerOpen = true
            waitForIdle()
            selections.value = helsinki
            pickerOpen = false
            waitForIdle()

            onNodeWithText("Updating forecast...").assertIsDisplayed()
            onNodeWithText("Location guide").assertIsDisplayed()
            onNodeWithText("23°").assertIsDisplayed()

            pending.remove(helsinki)?.complete(snapshot(helsinki))
            waitUntil(timeoutMillis = 5_000) { viewModel.profile.value?.place == helsinki }
            waitForIdle()
            onNodeWithText("-5°").assertIsDisplayed()
        }

    private fun weatherController(
        selections: MutableStateFlow<Place?>,
        pending: MutableMap<Place, CompletableDeferred<WeatherSnapshot>>,
    ) = AppBackgroundController(
        selectedPlaces = selections,
        loadWeather = { place ->
            val deferred = CompletableDeferred<WeatherSnapshot>()
            pending[place] = deferred
            Result.success(deferred.await())
        },
        resolvePlace = { amsterdam },
        now = { Clock.System.now() },
    )

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `guide stays expanded and shows the newly picked location`() =
        runDesktopComposeUiTest(width = 800, height = 1400) {
            val selections = MutableStateFlow<Place?>(amsterdam)
            val pending = mutableMapOf<Place, CompletableDeferred<WeatherSnapshot>>()
            val controller = weatherController(selections, pending)
            val viewModel = GuideViewModel { place, _ -> if (place == amsterdam) amsterdamProfile() else helsinkiProfile() }
            var pickerOpen by mutableStateOf(false)
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        val holder = rememberSaveableStateHolder()
                        holder.SaveableStateProvider("weather-page") {
                            Harness(controller, viewModel, pickerOpen)
                        }
                    }
                }
            }
            waitForIdle()
            pending.remove(amsterdam)?.complete(snapshot(amsterdam))
            waitForIdle()

            onNodeWithText("Location guide").performClick()
            waitForIdle()
            onNodeWithText("23°").assertIsDisplayed()

            // Pick Helsinki from the location picker, like WeatherScreen.select does.
            pickerOpen = true
            waitForIdle()
            selections.value = helsinki
            pickerOpen = false
            waitForIdle()
            pending.remove(helsinki)?.complete(snapshot(helsinki))
            waitForIdle()
            waitForIdle()

            // The guide must still be expanded and now show Helsinki, without any re-click.
            onNodeWithText("Winter").assertIsDisplayed()
            onNodeWithText("-5°").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `guide keeps the previous profile and spins while the new location loads`() =
        runDesktopComposeUiTest(width = 800, height = 1400) {
            val selections = MutableStateFlow<Place?>(amsterdam)
            val pending = mutableMapOf<Place, CompletableDeferred<WeatherSnapshot>>()
            val controller = weatherController(selections, pending)
            val climateGate = CompletableDeferred<ClimateProfile>()
            val viewModel =
                GuideViewModel { place, _ ->
                    if (place == amsterdam) amsterdamProfile() else climateGate.await()
                }
            var pickerOpen by mutableStateOf(false)
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        val holder = rememberSaveableStateHolder()
                        holder.SaveableStateProvider("weather-page") {
                            Harness(controller, viewModel, pickerOpen)
                        }
                    }
                }
            }
            waitForIdle()
            pending.remove(amsterdam)?.complete(snapshot(amsterdam))
            waitForIdle()

            onNodeWithText("Location guide").performClick()
            waitForIdle()
            onNodeWithText("23°").assertIsDisplayed()

            pickerOpen = true
            waitForIdle()
            selections.value = helsinki
            pickerOpen = false
            waitForIdle()
            pending.remove(helsinki)?.complete(snapshot(helsinki))
            waitForIdle()
            waitForIdle()

            // New place's climate still loading: spinner in the header, previous profile on screen.
            onNodeWithContentDescription("Loading location guide").assertIsDisplayed()
            onNodeWithText("23°").assertIsDisplayed()

            climateGate.complete(helsinkiProfile())
            waitUntil(timeoutMillis = 5_000) { viewModel.profile.value?.place == helsinki }
            waitForIdle()
            onNodeWithText("-5°").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `a failed climate fetch for the new place shows the not-available message instead of the stale chart`() =
        runDesktopComposeUiTest(width = 800, height = 1400) {
            val selections = MutableStateFlow<Place?>(amsterdam)
            val pending = mutableMapOf<Place, CompletableDeferred<WeatherSnapshot>>()
            val controller = weatherController(selections, pending)
            // Helsinki's climate fetch fails (returns null).
            val viewModel = GuideViewModel { place, _ -> if (place == amsterdam) amsterdamProfile() else null }
            var pickerOpen by mutableStateOf(false)
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        val holder = rememberSaveableStateHolder()
                        holder.SaveableStateProvider("weather-page") {
                            Harness(controller, viewModel, pickerOpen)
                        }
                    }
                }
            }
            waitForIdle()
            pending.remove(amsterdam)?.complete(snapshot(amsterdam))
            waitForIdle()

            onNodeWithText("Location guide").performClick()
            waitForIdle()
            onNodeWithText("23°").assertIsDisplayed()

            // Pick Helsinki: its climate fetch returns null.
            pickerOpen = true
            waitForIdle()
            selections.value = helsinki
            pickerOpen = false
            waitForIdle()
            pending.remove(helsinki)?.complete(snapshot(helsinki))
            waitUntil(timeoutMillis = 5_000) { !viewModel.loading.value && viewModel.profile.value == null }
            waitForIdle()

            // The guide says the data is unavailable; the stale Amsterdam chart is gone.
            onNodeWithText("No climate data available for this location yet.").assertIsDisplayed()
            onNodeWithText("23°").assertDoesNotExist()
        }
}
