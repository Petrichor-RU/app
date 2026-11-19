package nl.petrichor.app.data.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitDao
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryPiece
import nl.petrichor.app.data.wardrobe.ClothingDao
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType

/**
 * Room database for the whole app (wardrobe and outfit journal).
 * Uses Room's multiplatform support with SQLite bundled.
 * Version 4 upgrades model_state to store versioned RecommendationModelArtifact JSON payloads.
 * The platform builders use fallbackToDestructiveMigration(true), which the owner approved.
 */
@Database(
    entities = [
        ClothingPiece::class,
        ClothingType::class,
        OutfitEntry::class,
        OutfitEntryPiece::class,
        ModelState::class,
    ],
    version = 4,
    exportSchema = false,
)
@ConstructedBy(PetrichorDatabaseConstructor::class)
@ColumnTypeConverters(PetrichorConverters::class)
abstract class PetrichorDatabase : RoomDatabase() {
    abstract fun clothingDao(): ClothingDao

    abstract fun outfitDao(): OutfitDao

    companion object {
        const val DATABASE_NAME = "petrichor.db"
    }
}

@Suppress("KotlinNoActualForExpect")
expect object PetrichorDatabaseConstructor : RoomDatabaseConstructor<PetrichorDatabase> {
    override fun initialize(): PetrichorDatabase
}

// Database provider for multiplatform access
expect object PetrichorDatabaseProvider {
    fun getDatabase(): PetrichorDatabase
}

expect fun defaultClothingDao(): ClothingDao

expect fun defaultOutfitDao(): OutfitDao
