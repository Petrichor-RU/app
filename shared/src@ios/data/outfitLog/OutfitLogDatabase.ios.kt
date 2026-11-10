package nl.petrichor.app.data.outfitLog

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual object OutfitLogDatabaseProvider {

    @OptIn(ExperimentalForeignApi::class)
    private val dbInstance: OutfitLogDatabase by lazy {
        val directory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null
        )

        val path = requireNotNull(directory?.path) +
                "/" + OutfitLogDatabase.DATABASE_NAME

        Room.databaseBuilder<OutfitLogDatabase>(name = path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    actual fun getDatabase(): OutfitLogDatabase = dbInstance
}