package nl.petrichor.app.data.wardrobe

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import nl.petrichor.app.data.database.defaultClothingDao
import nl.petrichor.app.data.database.defaultOutfitDao
import nl.petrichor.app.data.media.MediaStorage
import nl.petrichor.app.data.outfit.OutfitDao
import nl.petrichor.app.data.outfit.OutfitLogRepository
import kotlin.time.Clock

/** Outcome of [WardrobeRepository.deletePieceWithArchive]. */
enum class PieceDeleteResult {
    /** Piece is linked to at least one journal entry: soft-deleted, image kept for journal display. */
    ARCHIVED,

    /** Piece is unlinked: hard-deleted together with its image. */
    DELETED,
}

/**
 * Repository for wardrobe operations
 * Handles data access and business logic
 */
class WardrobeRepository(
    private val clothingDao: ClothingDao = defaultClothingDao(),
    private val outfitDao: OutfitDao = defaultOutfitDao(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val imageDeleter: suspend (String) -> Unit = { path -> MediaStorage.deleteImage(path) },
) {
    // Get all clothing items (archived pieces are filtered out by the dao)
    fun getAllClothing(): Flow<List<ClothingPiece>> = clothingDao.getAll()

    // Built-in types are inserted on the first type-flow collection (idempotent via IGNORE).
    private var typesSeeded = false

    fun getClothingTypes(): Flow<List<ClothingType>> =
        clothingDao.getTypes().onStart {
            if (!typesSeeded) {
                typesSeeded = true
                clothingDao.insertTypes(BuiltInClothingTypes.all)
            }
        }

    // Add new clothing item
    suspend fun addClothing(clothing: ClothingPiece): Long =
        withContext(ioDispatcher) {
            clothingDao.insert(
                clothing
                    .takeUnless { it.createdAt != 0L }
                    ?.copy(createdAt = Clock.System.now().toEpochMilliseconds())
                    ?: clothing,
            )
        }

    // Update existing clothing item
    suspend fun updateClothing(clothing: ClothingPiece) =
        withContext(ioDispatcher) {
            clothingDao.update(clothing)
        }

    // Delete clothing item
    suspend fun deleteClothing(clothing: ClothingPiece) =
        withContext(ioDispatcher) {
            clothingDao.delete(clothing)
        }

    /**
     * Archive instead of delete (plan decision 13): a piece linked to at least one journal entry is
     * soft-deleted silently (its image file is kept, the journal still displays it); an unlinked
     * piece is hard-deleted together with its image.
     */
    suspend fun deletePieceWithArchive(piece: ClothingPiece): PieceDeleteResult =
        withContext(ioDispatcher) {
            if (outfitDao.getEntriesForPiece(piece.id).isNotEmpty()) {
                clothingDao.setArchived(piece.id, true)
                PieceDeleteResult.ARCHIVED
            } else {
                clothingDao.delete(piece)
                imageDeleter(piece.imagePath)
                PieceDeleteResult.DELETED
            }
        }

    suspend fun markLaundry(
        pieceId: Long,
        days: Int,
    ) = withContext(ioDispatcher) {
        clothingDao.setLaundryUntil(pieceId, OutfitLogRepository.laundryUntilEpochMs(days))
        _laundryVersion.update { it + 1 }
    }

    private val _laundryVersion = MutableStateFlow(0)

    /** Bumped on every laundry mark so recommendation UIs can recompute. */
    val laundryVersion: StateFlow<Int> = _laundryVersion.asStateFlow()

    fun pieceIsInLaundry(
        piece: ClothingPiece,
        nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean = piece.laundryUntilEpochMs > nowEpochMs
}
