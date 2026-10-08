package nl.petrichor.app.data.wardrobe

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for wardrobe functionality
 * Uses Room's multiplatform support with SQLite bundled
 */
@Database(
    entities = [ClothingPiece::class],
    version = 1,
    exportSchema = false
)
abstract class WardrobeDatabase : RoomDatabase() {
    
    abstract fun clothingDao(): ClothingDao

    companion object {
        const val DATABASE_NAME = "wardrobe-database.db"
    }
}

// Database provider for multiplatform access
expect object WardrobeDatabaseProvider {
    fun getDatabase(): WardrobeDatabase
}