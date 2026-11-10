package nl.petrichor.app.data.outfitLog

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class OutfitLogWithItems(
    @Embedded
    val log: OutfitLog,

    @Relation(
        parentColumn = "id",
        entityColumn = "outfitLogId"
    )
    val items: List<OutfitLogItem>
)

@Dao
interface OutfitLogDao {

    @Insert
    suspend fun insertLog(log: OutfitLog): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<OutfitLogItem>)

    @Transaction
    suspend fun insertOutfitWithItems(
        log: OutfitLog,
        items: List<OutfitLogItem>
    ): Long {
        require(log.comfortRating in 1..10) {
            "Comfort rating must be between 1 and 10."
        }

        require(items.isNotEmpty()) {
            "An outfit must contain at least one clothing item."
        }

        val logId = insertLog(log)

        insertItems(
            items.map { it.copy(outfitLogId = logId) }
        )

        return logId
    }

    @Transaction
    @Query("SELECT * FROM outfit_logs ORDER BY loggedAt DESC")
    fun getAllLogs(): Flow<List<OutfitLogWithItems>>

    @Transaction
    @Query("SELECT * FROM outfit_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): OutfitLogWithItems?

    @Query("DELETE FROM outfit_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("""
    UPDATE outfit_logs
    SET comfortRating = :rating
    WHERE id = :id
""")
    suspend fun updateComfortRating(id: Long, rating: Int): Int

    //returns the number of outfit logs without modifying anything
    @Query("SELECT COUNT(*) FROM outfit_logs")
    suspend fun getLogCount(): Int
}