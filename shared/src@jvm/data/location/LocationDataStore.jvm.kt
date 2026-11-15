package nl.petrichor.app.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import nl.petrichor.app.data.petrichorDataDirectory
import okio.Path.Companion.toPath
import java.io.File

internal actual fun createLocationDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        // petrichorDataDirectory() resolves ~/.petrichor-presentation in presentation mode.
        produceFile = { File(petrichorDataDirectory(), LOCATION_DATASTORE_NAME).absolutePath.toPath() },
    )
