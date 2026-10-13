package nl.petrichor.app.data.wardrobe

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * JVM implementation of WardrobeDatabaseProvider
 * Following Room KMP guide: https://developer.android.com/kotlin/multiplatform/room
 */
actual object WardrobeDatabaseProvider {
    private val dbInstance: WardrobeDatabase by lazy {
        val dbFile = File(System.getProperty("user.home"), ".petrichor/wardrobe-images" + File.separator + WardrobeDatabase.DATABASE_NAME)
        Room.databaseBuilder<WardrobeDatabase>(
            name = dbFile.absolutePath,
        )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    }
    
    actual fun getDatabase(): WardrobeDatabase = dbInstance
}
