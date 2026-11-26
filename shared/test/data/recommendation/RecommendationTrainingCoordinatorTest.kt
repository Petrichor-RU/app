package nl.petrichor.app.data.recommendation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.FakeOutfitDao
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryPiece
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.FakeClothingDao
import nl.petrichor.app.data.wardrobe.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationTrainingCoordinatorTest {
    private val today = LocalDate(2026, 11, 12)
    private val teeType = ClothingType(id = 1, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val jacketType = ClothingType(id = 2, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)

    private fun samplePiece(
        id: Long = 10L,
        typeId: Long = teeType.id,
        material: Material = Material.COTTON,
    ) = ClothingPiece(
        id = id,
        name = "Piece $id",
        typeId = typeId,
        material = material,
        notes = null,
        imagePath = "",
        thumbnailPath = "",
    )

    private fun surveyedEntry(
        id: Long,
        pieces: List<ClothingPiece>,
        score: Int = 5,
    ) = OutfitEntryWithPieces(
        OutfitEntry(
            id = id,
            wornOnDate = today,
            photoPath = null,
            notes = null,
            temperatureC = 15.0,
            feelsLikeC = 15.0,
            minTempC = 12.0,
            maxTempC = 18.0,
            weatherDescription = null,
            weatherIcon = null,
            isRainy = false,
            surveyFeelingScore = score,
            surveyWarmthVerdict = WarmthVerdict.PERFECT,
            surveyedAtEpochMs = 1_000L + id,
            createdAtEpochMs = 1_000L,
        ),
        pieces,
    )

    @Test
    fun `concurrent triggers coalesce into minimal runs and publish only the latest revision`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val outfitDao = FakeOutfitDao()
            val clothingDao = FakeClothingDao()
            clothingDao.types.value = listOf(teeType, jacketType)
            outfitDao.entries.value = listOf(surveyedEntry(1L, listOf(samplePiece())))

            val trainedRevisions = mutableListOf<Long>()
            val coordinator =
                RecommendationTrainingCoordinator(
                    outfitDao = outfitDao,
                    clothingDao = clothingDao,
                    ioDispatcher = dispatcher,
                    appScope = this,
                    trainer = { snapshot ->
                        trainedRevisions += snapshot.dataRevision
                        BayesianModelTrainer.train(snapshot)
                    },
                )

            // Five bursts before advancing the test dispatcher.
            repeat(5) { coordinator.notifyDataChanged() }
            advanceUntilIdle()

            // Coalesced: trained once with final revision 5 (no stale intermediate runs published).
            assertEquals(listOf(5L), trainedRevisions)
            val decoded = DecodedModel.decode(outfitDao.getModelState())
            assertNotNull(decoded)
            assertEquals(5L, decoded.dataRevision)
        }

    @Test
    fun `stale run never overwrites a newer revision triggered mid-training`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val outfitDao = FakeOutfitDao()
            val clothingDao = FakeClothingDao()
            clothingDao.types.value = listOf(teeType, jacketType)
            outfitDao.entries.value = listOf(surveyedEntry(1L, listOf(samplePiece())))

            lateinit var coordinator: RecommendationTrainingCoordinator
            var firstRunTriggeredSecond = false
            coordinator =
                RecommendationTrainingCoordinator(
                    outfitDao = outfitDao,
                    clothingDao = clothingDao,
                    ioDispatcher = dispatcher,
                    appScope = this,
                    trainer = { snapshot ->
                        if (!firstRunTriggeredSecond) {
                            firstRunTriggeredSecond = true
                            // Mutate during training so revision advances while run 1 is in flight.
                            coordinator.notifyDataChanged()
                        }
                        BayesianModelTrainer.train(snapshot)
                    },
                )

            coordinator.notifyDataChanged()
            advanceUntilIdle()

            // Revision 1 must not have been inserted; only revision 2 was persisted.
            assertEquals(listOf(2L), outfitDao.modelStates.mapNotNull { DecodedModel.decode(it)?.dataRevision })
        }

    @Test
    fun `outfit update retrains and deleting last usable entry invalidates model state`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val outfitDao = FakeOutfitDao()
            val clothingDao = FakeClothingDao()
            clothingDao.types.value = listOf(teeType, jacketType)
            val piece = samplePiece(id = 7L, typeId = teeType.id, material = Material.COTTON)
            clothingDao.clothing.value = listOf(piece)
            val entryWithPieces = surveyedEntry(id = 1L, pieces = listOf(piece), score = 4)
            outfitDao.entries.value = listOf(entryWithPieces)
            outfitDao.links += OutfitEntryPiece(entryId = 1L, pieceId = 7L)

            val outfitRepo =
                OutfitLogRepository(
                    outfitDao = outfitDao,
                    clothingDao = clothingDao,
                    ioDispatcher = dispatcher,
                    imageDeleter = {},
                    appScope = this,
                )

            // 1. Outfit edit retrains.
            outfitRepo.updateOutfit(entryWithPieces.entry.copy(surveyFeelingScore = 5), listOf(piece))
            advanceUntilIdle()
            assertNotNull(DecodedModel.decode(outfitDao.getModelState()))

            // 2. Deleting the last usable entry invalidates ModelState cleanly.
            outfitRepo.deleteEntry(entryWithPieces.entry)
            advanceUntilIdle()
            assertNull(DecodedModel.decode(outfitDao.getModelState()))
            assertEquals(0, outfitDao.getModelState()?.sampleCount)
        }
}
