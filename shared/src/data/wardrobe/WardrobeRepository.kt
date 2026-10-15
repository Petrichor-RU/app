package nl.petrichor.app.data.wardrobe

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlin.time.Clock

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

    fun getClothingTypes(): Flow<List<ClothingType>> =
        clothingDao.getTypes().onStart {
            clothingDao.insertTypes(BuiltInClothingTypes.all)
        }

    // Add new clothing item
    suspend fun addClothing(clothing: ClothingPiece): Long = withContext(ioDispatcher) {
        clothingDao.insert(
            clothing.takeUnless { it.createdAt != 0L }
                ?.copy(createdAt = Clock.System.now().toEpochMilliseconds())
                ?: clothing
        )
    }

    // Update existing clothing item
    suspend fun updateClothing(clothing: ClothingPiece) = withContext(ioDispatcher) {
        clothingDao.update(clothing)
    }

    // Delete clothing item
    suspend fun deleteClothing(clothing: ClothingPiece) = withContext(ioDispatcher) {
        clothingDao.delete(clothing)
    }
}