@file:Suppress("MatchingDeclarationName") // platform file hosts the provider object and the dao accessors

package nl.petrichor.app.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import nl.petrichor.app.data.outfit.OutfitDao
import nl.petrichor.app.data.wardrobe.ClothingDao
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * iOS implementation of PetrichorDatabaseProvider
 * Following Room KMP guide: https://developer.android.com/kotlin/multiplatform/room
 */
actual object PetrichorDatabaseProvider {
    private val dbInstance: PetrichorDatabase by lazy {
        val dbFilePath = documentDirectory() + "/" + PetrichorDatabase.DATABASE_NAME
        Room
            .databaseBuilder<PetrichorDatabase>(
                name = dbFilePath,
            ).setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .fallbackToDestructiveMigration(true)
            .build()
    }

    actual fun getDatabase(): PetrichorDatabase = dbInstance
}

actual fun defaultClothingDao(): ClothingDao = PetrichorDatabaseProvider.getDatabase().clothingDao()

actual fun defaultOutfitDao(): OutfitDao = PetrichorDatabaseProvider.getDatabase().outfitDao()

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
    return requireNotNull(documentDirectory?.path)
}
