package nl.petrichor.app.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import nl.petrichor.app.data.isPresentationMode
import okio.Path.Companion.toPath

private const val PRESENTATION_DATASTORE_NAME = "saved_locations-presentation.preferences_pb"

internal actual fun createLocationDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        // Presentation mode (URL ?mode=presentation) uses a suffixed store so the browser
        // profile's real saved locations are untouched.
        produceFile = {
            val name = if (isPresentationMode()) PRESENTATION_DATASTORE_NAME else LOCATION_DATASTORE_NAME
            name.toPath()
        },
    )
