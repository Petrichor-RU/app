@file:Suppress("MatchingDeclarationName") // platform file hosts the provider object and the dao accessors

package nl.petrichor.app.data.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import nl.petrichor.app.data.outfit.OutfitDao
import nl.petrichor.app.data.wardrobe.ClothingDao

/**
 * Android implementation of PetrichorDatabaseProvider
 * Following Room KMP guide: https://developer.android.com/kotlin/multiplatform/room
 */
actual object PetrichorDatabaseProvider {
    private var dbInstance: PetrichorDatabase? = null

    // This will be set by the Android app during initialization
    fun init(context: Context) {
        if (dbInstance == null) {
            dbInstance =
                Room
                    .databaseBuilder<PetrichorDatabase>(
                        context = context.applicationContext,
                        name = PetrichorDatabase.DATABASE_NAME,
                    ).setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .fallbackToDestructiveMigration(true)
                    .build()
        }
    }

    actual fun getDatabase(): PetrichorDatabase =
        dbInstance
            ?: error("PetrichorDatabase not initialized. Call PetrichorDatabaseProvider.init(context) first.")
}

actual fun defaultClothingDao(): ClothingDao = PetrichorDatabaseProvider.getDatabase().clothingDao()

actual fun defaultOutfitDao(): OutfitDao = PetrichorDatabaseProvider.getDatabase().outfitDao()
