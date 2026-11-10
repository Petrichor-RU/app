package nl.petrichor.app.data.outfitLog

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

actual object OutfitLogDatabaseProvider {

    private val dbInstance: OutfitLogDatabase by lazy {
        val directory = File(
            System.getProperty("user.home"),
            ".petrichor/outfit-logs"
        ).apply {
            check(mkdirs() || isDirectory)
        }

        val dbFile = File(
            directory,
            OutfitLogDatabase.DATABASE_NAME
        )

        Room.databaseBuilder<OutfitLogDatabase>(
            name = dbFile.absolutePath
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    actual fun getDatabase(): OutfitLogDatabase = dbInstance
}