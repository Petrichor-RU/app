@file:Suppress("MatchingDeclarationName") // platform file hosts the provider object and the dao accessors

package nl.petrichor.app.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import nl.petrichor.app.data.outfit.OutfitDao
import nl.petrichor.app.data.wardrobe.ClothingDao
import java.io.File

/**
 * JVM implementation of PetrichorDatabaseProvider
 * Following Room KMP guide: https://developer.android.com/kotlin/multiplatform/room
 */
actual object PetrichorDatabaseProvider {
    private val dbInstance: PetrichorDatabase by lazy {
        val dbDirectory =
            File(System.getProperty("user.home"), ".petrichor")
                .apply { check(mkdirs() || isDirectory) { "Could not create petrichor database directory." } }
        val dbFile = File(dbDirectory, PetrichorDatabase.DATABASE_NAME)
        Room
            .databaseBuilder<PetrichorDatabase>(
                name = dbFile.absolutePath,
            ).setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    actual fun getDatabase(): PetrichorDatabase = dbInstance
}

actual fun defaultClothingDao(): ClothingDao = PetrichorDatabaseProvider.getDatabase().clothingDao()

actual fun defaultOutfitDao(): OutfitDao = PetrichorDatabaseProvider.getDatabase().outfitDao()
