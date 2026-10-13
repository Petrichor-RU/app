package nl.petrichor.app.data.wardrobe

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * iOS implementation of WardrobeDatabaseProvider
 * Following Room KMP guide: https://developer.android.com/kotlin/multiplatform/room
 */
actual object WardrobeDatabaseProvider {
    private val dbInstance: WardrobeDatabase by lazy {
        val dbFilePath = documentDirectory() + "/" + WardrobeDatabase.DATABASE_NAME
        Room.databaseBuilder<WardrobeDatabase>(
            name = dbFilePath,
        )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    }
    
    actual fun getDatabase(): WardrobeDatabase = dbInstance
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}
