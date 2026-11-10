package nl.petrichor.app.data.outfitLog

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

actual object OutfitLogDatabaseProvider {

    @Volatile
    private var dbInstance: OutfitLogDatabase? = null

    fun init(context: Context) {
        if (dbInstance == null) {
            synchronized(this) {
                if (dbInstance == null) {
                    dbInstance = Room.databaseBuilder<OutfitLogDatabase>(
                        context = context.applicationContext,
                        name = OutfitLogDatabase.DATABASE_NAME
                    )
                        .setDriver(BundledSQLiteDriver())
                        .setQueryCoroutineContext(Dispatchers.IO)
                        .build()
                }
            }
        }
    }

    actual fun getDatabase(): OutfitLogDatabase {
        return dbInstance
            ?: throw IllegalStateException(
                "OutfitLogDatabase not initialized."
            )
    }
}