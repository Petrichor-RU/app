package nl.petrichor.app.data.wardrobe

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository for wardrobe operations
 * Handles data access and business logic
 */
class WardrobeRepository(
    private val clothingDao: ClothingDao = WardrobeDatabaseProvider.getDatabase().clothingDao(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    
    // Get all clothing items
    fun getAllClothing(): Flow<List<ClothingPiece>> = clothingDao.getAll()

    // Get clothing by type
    fun getClothingByType(type: ClothingType): Flow<List<ClothingPiece>> = clothingDao.getByType(type)

    // Get single clothing item
    suspend fun getClothingItem(id: Long): ClothingPiece? = withContext(ioDispatcher) {
        clothingDao.getById(id)
    }

    // Add new clothing item
    suspend fun addClothing(clothing: ClothingPiece): Long = withContext(ioDispatcher) {
        clothingDao.insert(clothing)
    }

    // Update existing clothing item
    suspend fun updateClothing(clothing: ClothingPiece) = withContext(ioDispatcher) {
        clothingDao.update(clothing)
    }

    // Delete clothing item
    suspend fun deleteClothing(clothing: ClothingPiece) = withContext(ioDispatcher) {
        clothingDao.delete(clothing)
    }

    // Delete all clothing (for testing/clearing)
    suspend fun deleteAllClothing() = withContext(ioDispatcher) {
        clothingDao.deleteAll()
    }
}