package nl.petrichor.app.data.outfitLog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import nl.petrichor.app.data.wardrobe.ClothingDao
import kotlin.time.Clock
import nl.petrichor.app.data.wardrobe.WardrobeDatabaseProvider

class OutfitLogRepository(
    private val outfitLogDao: OutfitLogDao =
        OutfitLogDatabaseProvider.getDatabase().outfitLogDao(),

    private val clothingDao: ClothingDao =
        WardrobeDatabaseProvider.getDatabase().clothingDao(),

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun logOutfit(
        photoPath: String,
        comfortRating: Int,
        wardrobeClothingIds: List<Long>
    ): Long = withContext(ioDispatcher) {

        require(photoPath.isNotBlank())
        require(comfortRating in 1..10)
        require(wardrobeClothingIds.isNotEmpty())
        require(wardrobeClothingIds.distinct().size == wardrobeClothingIds.size)

        val snapshotItems = wardrobeClothingIds.map { clothingId ->

            val clothing = clothingDao.getById(clothingId)
                ?: throw IllegalArgumentException(
                    "Clothing item $clothingId does not exist."
                )

            val clothingType = clothingDao.getTypeById(clothing.typeId)
                ?: throw IllegalStateException(
                    "Clothing type not found for item $clothingId."
                )

            OutfitLogItem(
                outfitLogId = 0L,
                wardrobeClothingId = clothing.id,
                clothingNameSnapshot = clothing.name,
                clothingTypeSnapshot = clothingType.name,
                materialSnapshot = clothing.material.displayName,
                notesSnapshot = clothing.notes
            )
        }

        outfitLogDao.insertOutfitWithItems(
            log = OutfitLog(
                photoPath = photoPath,
                comfortRating = comfortRating,
                loggedAt = Clock.System.now().toEpochMilliseconds()
            ),
            items = snapshotItems
        )
    }

    fun getAllLogs(): Flow<List<OutfitLogWithItems>> =
        outfitLogDao.getAllLogs()

    suspend fun getLogById(id: Long): OutfitLogWithItems? =
        withContext(ioDispatcher) {
            outfitLogDao.getLogById(id)
        }

    suspend fun deleteLog(id: Long) =
        withContext(ioDispatcher) {
            outfitLogDao.deleteLog(id)
        }

    suspend fun updateComfortRating(id: Long, rating: Int) =
        withContext(ioDispatcher) {
            require(rating in 1..10)
            outfitLogDao.updateComfortRating(id, rating)
        }
}