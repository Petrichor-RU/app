package nl.petrichor.app.data.wardrobe

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Android implementation of WardrobeDatabaseProvider
 * Following Room KMP guide: https://developer.android.com/kotlin/multiplatform/room
 */
actual object WardrobeDatabaseProvider {
    private var dbInstance: WardrobeDatabase? = null
    
    // This will be set by the Android app during initialization
    fun init(context: Context) {
        if (dbInstance == null) {
            dbInstance = Room.databaseBuilder<WardrobeDatabase>(
                context = context.applicationContext,
                name = WardrobeDatabase.DATABASE_NAME,
            )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        }
    }
    
    actual fun getDatabase(): WardrobeDatabase {
        return dbInstance ?: throw IllegalStateException("WardrobeDatabase not initialized. Call WardrobeDatabaseProvider.init(context) first.")
    }
}