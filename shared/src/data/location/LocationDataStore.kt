package nl.petrichor.app.data.location

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal const val LOCATION_DATASTORE_NAME = "saved_locations.preferences_pb"

/**
 * Where the preferences file lives differs per platform (app files dir, iOS documents dir, browser localStorage),
 * so creating the store is left to the platform specific code.
 */
internal expect fun createLocationDataStore(): DataStore<Preferences>

/**
 * DataStore must only ever have one active instance per file, so this is shared by every [SavedLocationsRepository].
 */
internal val locationDataStore: DataStore<Preferences> by lazy { createLocationDataStore() }
