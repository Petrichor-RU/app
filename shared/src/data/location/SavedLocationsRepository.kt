package nl.petrichor.app.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val savedLocationsKey = stringPreferencesKey("saved_locations")

class SavedLocationsRepository(
    private val dataStore: DataStore<Preferences> = locationDataStore,
) {
    val locations: Flow<List<LocationResult>> = dataStore.data.map { prefs ->
        prefs[savedLocationsKey]?.let { raw ->
            runCatching { Json.decodeFromString<List<LocationResult>>(raw) }.getOrNull()
        }.orEmpty()
    }

    suspend fun update(transform: (List<LocationResult>) -> List<LocationResult>) {
        dataStore.edit { prefs ->
            val current = prefs[savedLocationsKey]?.let { raw ->
                runCatching { Json.decodeFromString<List<LocationResult>>(raw) }.getOrNull()
            }.orEmpty()
            prefs[savedLocationsKey] = Json.encodeToString(transform(current))
        }
    }

    suspend fun add(location: LocationResult) = update { current ->
        if (current.any { it.key == location.key }) current else current + location
    }

    suspend fun remove(location: LocationResult) = update { current ->
        current.filterNot { it.key == location.key }
    }

}