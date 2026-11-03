package nl.petrichor.app.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.io.File

internal actual fun createLocationDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    produceFile = { File(System.getProperty("user.home"), ".petrichor/$LOCATION_DATASTORE_NAME").absolutePath.toPath() },
)
