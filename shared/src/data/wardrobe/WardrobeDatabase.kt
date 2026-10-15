package nl.petrichor.app.data.wardrobe

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

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
@TypeConverters(WardrobeConverters::class)
abstract class WardrobeDatabase : RoomDatabase() {

    abstract fun clothingDao(): ClothingDao

    companion object {
        const val DATABASE_NAME = "wardrobe-database.db"
    }
}

expect object WardrobeDatabaseConstructor : RoomDatabaseConstructor<WardrobeDatabase> {
    override fun initialize(): WardrobeDatabase
}

// Database provider for multiplatform access
expect object WardrobeDatabaseProvider {
    fun getDatabase(): WardrobeDatabase
}