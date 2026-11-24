package nl.petrichor.app.data.background

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.ui.background.AppBackgroundController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AppBackgroundControllerTest {
    private val first = Place("First", Coordinates(1.0, 1.0))
    private val second = Place("Second", Coordinates(2.0, 2.0))
    private val start = Instant.parse("2026-11-13T16:59:00Z")

    @Test
    fun `one weather load per selection and ticker advances the time band`() =
        runTest {
            val selected = MutableStateFlow<Place?>(first)
            var loads = 0
            var now = start
            val controller =
                AppBackgroundController(
                    selectedPlaces = selected,
                    loadWeather = {
                        loads++
                        Result.success(snapshot().copy(place = it))
                    },
                    now = { now },
                )
            val job = backgroundScope.launch { controller.run() }
            runCurrent()
            assertEquals(1, loads)
            assertEquals(
                first,
                controller.weather.value.weather
                    ?.place,
            )
            assertEquals(TimeBand.AFTERNOON, controller.condition.value?.band)
            now += 1.minutes
            advanceTimeBy(60_001.milliseconds)
            assertEquals(TimeBand.DUSK, controller.condition.value?.band)
            assertEquals(1, loads)
            selected.value = second
            runCurrent()
            assertEquals(2, loads)
            assertEquals(
                second,
                controller.weather.value.weather
                    ?.place,
            )
            job.cancel()
        }

    @Test
    fun `changing selection cancels an older in flight request`() =
        runTest {
            val selected = MutableStateFlow<Place?>(first)
            val pending = CompletableDeferred<Unit>()
            val controller =
                AppBackgroundController(
                    selectedPlaces = selected,
                    loadWeather = {
                        if (it == first) pending.await()
                        Result.success(snapshot(icon = "13d", conditionId = 600).copy(place = it))
                    },
                    now = { start },
                )
            backgroundScope.launch { controller.run() }
            runCurrent()
            assertTrue(controller.weather.value.loading)
            selected.value = second
            runCurrent()
            pending.complete(Unit)
            runCurrent()
            assertEquals(
                second,
                controller.weather.value.weather
                    ?.place,
            )
            assertEquals(WeatherMood.SNOW, controller.condition.value?.mood)
        }

    @Test
    fun `errors clear old weather and lifecycle restart does not duplicate a completed load`() =
        runTest {
            val selected = MutableStateFlow<Place?>(first)
            var loads = 0
            val controller =
                AppBackgroundController(
                    selectedPlaces = selected,
                    loadWeather = {
                        loads++
                        Result.failure(IllegalStateException("Offline"))
                    },
                    now = { start },
                )
            val job = backgroundScope.launch { controller.run() }
            runCurrent()
            assertFalse(controller.weather.value.loading)
            assertEquals("Offline", controller.weather.value.error)
            assertNull(controller.weather.value.weather)
            assertEquals(WeatherMood.PARTLY_CLOUDY, controller.condition.value?.mood)
            job.cancel()
            runCurrent()
            backgroundScope.launch { controller.run() }
            runCurrent()
            assertEquals(1, loads)
        }
}
