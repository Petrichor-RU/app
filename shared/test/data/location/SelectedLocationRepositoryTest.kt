package nl.petrichor.app.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectedLocationRepositoryTest {
    private val utrecht = Place("Utrecht", Coordinates(52.0907, 5.1214))

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
