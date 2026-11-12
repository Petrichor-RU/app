package nl.petrichor.app.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.weather.Place

private val selectedLocationKey = stringPreferencesKey("selected_location")

class SelectedLocationRepository(
    private val dataStore: DataStore<Preferences> = locationDataStore,
) {
    val selected: Flow<Place?> = dataStore.data
        .map { prefs ->
            prefs[selectedLocationKey]?.let { raw ->
                runCatching { Json.decodeFromString<Place>(raw) }.getOrNull()
            }
        }
        // The store is shared with the saved locations list, so ignore emissions caused by unrelated edits.
        .distinctUntilChanged()

    suspend fun select(place: Place?) {
        dataStore.edit { prefs ->
            if (place == null) {
                prefs.remove(selectedLocationKey)
            } else {
                prefs[selectedLocationKey] = Json.encodeToString(place)
            }
        }
    }
}
