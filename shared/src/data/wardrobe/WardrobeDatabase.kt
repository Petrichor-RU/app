package nl.petrichor.app.data.wardrobe

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.ColumnTypeConverters

/**
 * Room database for wardrobe functionality
 * Uses Room's multiplatform support with SQLite bundled
 */
@Database(
    entities = [ClothingPiece::class, ClothingType::class],
    version = 1,
    exportSchema = false
)
@ConstructedBy(WardrobeDatabaseConstructor::class)
@ColumnTypeConverters(WardrobeConverters::class)
abstract class WardrobeDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao

    companion object {
        const val DATABASE_NAME = "wardrobe-database.db"
    }
}

@Suppress("KotlinNoActualForExpect")
expect object WardrobeDatabaseConstructor : RoomDatabaseConstructor<WardrobeDatabase> {
    override fun initialize(): WardrobeDatabase
}

// Database provider for multiplatform access
expect object WardrobeDatabaseProvider {
    fun getDatabase(): WardrobeDatabase
}

expect fun defaultClothingDao(): ClothingDao