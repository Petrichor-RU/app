package nl.petrichor.app.data.outfitLog

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        OutfitLog::class,
        OutfitLogItem::class
    ],
    version = 1,
    exportSchema = false
)
@ConstructedBy(OutfitLogDatabaseConstructor::class)
abstract class OutfitLogDatabase : RoomDatabase() {

    abstract fun outfitLogDao(): OutfitLogDao

    companion object {
        const val DATABASE_NAME = "outfit-log-database.db"
    }
}

expect object OutfitLogDatabaseConstructor :
    RoomDatabaseConstructor<OutfitLogDatabase> {
    override fun initialize(): OutfitLogDatabase
}

expect object OutfitLogDatabaseProvider {
    fun getDatabase(): OutfitLogDatabase
}