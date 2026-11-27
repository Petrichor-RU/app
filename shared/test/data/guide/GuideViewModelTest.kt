package nl.petrichor.app.data.guide

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.ui.weather.GuideViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModelTest {
    private val amsterdam = Place("Amsterdam", Coordinates(52.37, 4.89))
    private val helsinki = Place("Helsinki", Coordinates(60.17, 24.94))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun profile(place: Place) = ClimateProfile(place, List(12) { MonthClimate(it + 1, 10.0, 2.0, 30.0, 50) })

    @Test
    fun `successful load publishes the profile and clears loading`() =
        runTest {
            val viewModel = GuideViewModel { place, _ -> profile(place) }

            viewModel.ensureClimateLoaded(amsterdam, 60)
            advanceUntilIdle()

            assertEquals(amsterdam, viewModel.profile.value?.place)
            assertFalse(viewModel.loading.value)
        }

    @Test
    fun `same place is not refetched while in flight or loaded`() =
        runTest {
            var loads = 0
            val gate = CompletableDeferred<ClimateProfile>()
            val viewModel =
                GuideViewModel { place, _ ->
                    loads++
                    gate.await()
                    profile(place)
                }

            viewModel.ensureClimateLoaded(amsterdam, 60)
            advanceUntilIdle()
            viewModel.ensureClimateLoaded(amsterdam, 60)
            advanceUntilIdle()
            assertEquals(1, loads)
            assertTrue(viewModel.loading.value)

            gate.complete(profile(amsterdam))
            advanceUntilIdle()
            assertFalse(viewModel.loading.value)
            viewModel.ensureClimateLoaded(amsterdam, 60)
            advanceUntilIdle()
            assertEquals(1, loads)
        }

    @Test
    fun `failed load keeps the profile empty and a later call retries`() =
        runTest {
            var loads = 0
            val viewModel =
                GuideViewModel { _, _ ->
                    loads++
                    null
                }

            viewModel.ensureClimateLoaded(amsterdam, 60)
            advanceUntilIdle()
            assertNull(viewModel.profile.value)
            assertFalse(viewModel.loading.value)

            viewModel.ensureClimateLoaded(amsterdam, 60)
            advanceUntilIdle()
            assertEquals(2, loads)
        }

    @Test
    fun `failed fetch for a new place clears the profile so the guide says no data and a retry is not blocked`() =
        runTest {
            var fail = true
            val viewModel =
                GuideViewModel { place, _ ->
                    when {
                        place == amsterdam -> profile(amsterdam)
                        fail -> null
                        else -> profile(place)
                    }
                }

            viewModel.ensureClimateLoaded(amsterdam, 60)
            advanceUntilIdle()
            assertEquals(amsterdam, viewModel.profile.value?.place)

            // The new place's fetch returns null: the stale profile is cleared (the expanded guide
            // then shows its "no climate data available" message), loading clears...
            viewModel.ensureClimateLoaded(helsinki, 60)
            advanceUntilIdle()
            assertNull(viewModel.profile.value)
            assertFalse(viewModel.loading.value)

            // ...and a later call retries instead of being blocked.
            fail = false
            viewModel.ensureClimateLoaded(helsinki, 60)
            advanceUntilIdle()
            assertEquals(helsinki, viewModel.profile.value?.place)
            assertFalse(viewModel.loading.value)
        }

    @Test
    fun `expansion flag toggles and is exposed as state`() =
        runTest {
            val viewModel = GuideViewModel { _, _ -> null }

            assertFalse(viewModel.expanded.value)
            viewModel.toggleExpanded()
            assertTrue(viewModel.expanded.value)
            viewModel.toggleExpanded()
            assertFalse(viewModel.expanded.value)
        }

    @Test
    fun `switching place keeps the old profile while loading and drops a stale result`() =
        runTest {
            val amsterdamGate = CompletableDeferred<ClimateProfile>()
            val helsinkiGate = CompletableDeferred<ClimateProfile>()
            val viewModel =
                GuideViewModel { place, _ ->
                    if (place == amsterdam) amsterdamGate.await() else helsinkiGate.await()
                }

            viewModel.ensureClimateLoaded(amsterdam, 60)
            advanceUntilIdle()
            amsterdamGate.complete(profile(amsterdam))
            advanceUntilIdle()
            assertEquals(amsterdam, viewModel.profile.value?.place)

            // New place: old profile stays on screen while the new one loads.
            viewModel.ensureClimateLoaded(helsinki, 60)
            advanceUntilIdle()
            assertTrue(viewModel.loading.value)
            assertEquals(amsterdam, viewModel.profile.value?.place)

            helsinkiGate.complete(profile(helsinki))
            advanceUntilIdle()
            assertEquals(helsinki, viewModel.profile.value?.place)
            assertFalse(viewModel.loading.value)

            // A late result for the previous place must not overwrite the current one.
            amsterdamGate.complete(profile(amsterdam))
            advanceUntilIdle()
            assertEquals(helsinki, viewModel.profile.value?.place)
            assertFalse(viewModel.loading.value)
        }
}
