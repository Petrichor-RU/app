package nl.petrichor.app.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.weather.Place

private val selectedPlaceKey = stringPreferencesKey("selected_place")

/**
 * Persisted, shared selected location: both the Weather tab and
 * the Suggestions tab use this place. `null` means "use current location".
 * Stored as one JSON preference key in the existing saved_locations DataStore.
 */
class SelectedLocationStore(
    private val dataStore: DataStore<Preferences> = locationDataStore,
) {
    val selectedPlace: Flow<Place?> =
        dataStore.data.map { prefs ->
            prefs[selectedPlaceKey]?.let { raw ->
                runCatching { Json.decodeFromString<Place>(raw) }.getOrNull()
            }
        }

    suspend fun select(place: Place) {
        dataStore.edit { prefs ->
            prefs[selectedPlaceKey] = Json.encodeToString(place)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            // DataStore 1.3.0's MutablePreferences.remove unwraps its return as a non-null T,
            // which throws when the key is absent on Kotlin/Wasm; guard removal on existence.
            if (selectedPlaceKey in prefs) {
                prefs.remove(selectedPlaceKey)
            }
        }
    }
}
