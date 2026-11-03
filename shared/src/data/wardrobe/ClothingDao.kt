package nl.petrichor.app.data.wardrobe

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for clothing entities
 */
@Dao
interface ClothingDao {

    @Query("SELECT * FROM clothing ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ClothingPiece>>

    @Query("SELECT * FROM clothing WHERE typeId = :typeId ORDER BY createdAt DESC")
    fun getByType(typeId: Long): Flow<List<ClothingPiece>>

    @Query("SELECT * FROM clothing WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ClothingPiece?

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