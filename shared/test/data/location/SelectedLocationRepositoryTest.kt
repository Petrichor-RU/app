package nl.petrichor.app.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectedLocationRepositoryTest {
    private val utrecht = Place("Utrecht", Coordinates(52.0907, 5.1214))
    private val rotterdam = Place("Rotterdam", Coordinates(51.9244, 4.4777))

    @Test
    fun `nothing selected means the current location is used`() = runBlocking {
        val repository = SelectedLocationRepository(FakePreferencesDataStore())

        assertNull(repository.selected.first())
    }

    @Test
    fun `a selected place is persisted and read back`() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        SelectedLocationRepository(dataStore).select(utrecht)

        assertEquals(utrecht, SelectedLocationRepository(dataStore).selected.first())
    }

    @Test
    fun `selecting the current location clears the saved place`() = runBlocking {
        val repository = SelectedLocationRepository(FakePreferencesDataStore())
        repository.select(utrecht)

        repository.select(null)

        assertNull(repository.selected.first())
    }

    @Test
    fun `editing saved locations does not re-emit the selection`() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        val selectedRepository = SelectedLocationRepository(dataStore)
        val savedRepository = SavedLocationsRepository(dataStore)
        val rotterdamResult = LocationResult("Rotterdam", coordinates = rotterdam.coordinates)
        selectedRepository.select(utrecht)

        val emissions = mutableListOf<Place?>()
        val collector = launch(Dispatchers.Unconfined) { selectedRepository.selected.toList(emissions) }
        savedRepository.add(rotterdamResult)
        yield()
        savedRepository.remove(rotterdamResult)
        yield()
        selectedRepository.select(rotterdam)
        yield()
        collector.cancel()

        assertEquals<List<Place?>>(listOf(utrecht, rotterdam), emissions)
    }
}

private class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
