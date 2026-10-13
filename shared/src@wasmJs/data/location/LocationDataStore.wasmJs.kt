package nl.petrichor.app.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.WebLocalStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer

/**
 * The path based factory on web falls back to sessionStorage, which is wiped when the tab closes, so use
 * localStorage explicitly to keep saved locations between visits.
 */
internal actual fun createLocationDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    storage = WebLocalStorage(PreferencesSerializer, LOCATION_DATASTORE_NAME),
)
