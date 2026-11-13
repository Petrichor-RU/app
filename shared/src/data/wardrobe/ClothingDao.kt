package nl.petrichor.app.data.wardrobe

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for clothing entities
 */
@Dao
@Suppress("TooManyFunctions") // DAO query surface; each method is a single query
interface ClothingDao {
    @Query("SELECT * FROM clothing WHERE archived = 0 ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ClothingPiece>>

    @Query("SELECT * FROM clothing WHERE typeId = :typeId AND archived = 0 ORDER BY createdAt DESC")
    fun getByType(typeId: Long): Flow<List<ClothingPiece>>

    @Query("SELECT * FROM clothing WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ClothingPiece?

    // Archived pieces are only reachable through journal entries, not through the wardrobe UI.
    @Query("SELECT * FROM clothing WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludingArchived(id: Long): ClothingPiece?

    @Query("UPDATE clothing SET archived = :archived WHERE id = :id")
    suspend fun setArchived(
        id: Long,
        archived: Boolean,
    )

    @Query("UPDATE clothing SET laundryUntilEpochMs = :untilEpochMs WHERE id = :id")
    suspend fun setLaundryUntil(
        id: Long,
        untilEpochMs: Long,
    )

    @Insert
    suspend fun insert(clothing: ClothingPiece): Long

    @Update
    suspend fun update(clothing: ClothingPiece)

    @Delete
    suspend fun delete(clothing: ClothingPiece)

    @Query("DELETE FROM clothing")
    suspend fun deleteAll()

    @Query("SELECT * FROM clothing_types ORDER BY supertype, name")
    fun getTypes(): Flow<List<ClothingType>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTypes(types: List<ClothingType>)

    @Insert
    suspend fun insertType(type: ClothingType): Long
}
