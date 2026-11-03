package nl.petrichor.app.data.location

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

private lateinit var applicationContext: Context

/**
 * Android needs a [Context] to know where the app's files directory is. Call this from the activity before showing
 * any screen that uses saved locations.
 */
fun initLocationDataStore(context: Context) {
    applicationContext = context.applicationContext
}

internal actual fun createLocationDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    produceFile = { applicationContext.filesDir.resolve(LOCATION_DATASTORE_NAME).absolutePath.toPath() },
)
