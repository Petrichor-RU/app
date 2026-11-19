package nl.petrichor.app.data.recommendation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.FakeOutfitDao
import nl.petrichor.app.data.outfit.ModelState
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
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
                        RidgeModelTrainer.train(snapshot)
                    },
                )

            // Five bursts before advancing the test dispatcher.
            repeat(5) { coordinator.notifyDataChanged() }
            advanceUntilIdle()

            // Coalesced: trained once with final revision 5 (no stale intermediate runs published).
            assertEquals(listOf(5L), trainedRevisions)
            val state = outfitDao.getModelState()
            assertNotNull(state)
            assertEquals(5L, state.dataRevision)
            assertEquals(FeatureExtractor.FEATURE_SCHEMA_VERSION, state.schemaVersion)
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
                        RidgeModelTrainer.train(snapshot)
                    },
                )

            coordinator.notifyDataChanged()
            advanceUntilIdle()

            // Revision 1 must not have been inserted; only revision 2 was persisted.
            assertEquals(listOf(2L), outfitDao.modelStates.map { it.dataRevision })
        }

    @Test
    fun `outfit update and clothing material or type edit retrain and deleting last entry clears model`() =
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

            val coordinator =
                RecommendationTrainingCoordinator(
                    outfitDao = outfitDao,
                    clothingDao = clothingDao,
                    ioDispatcher = dispatcher,
                    appScope = this,
                )
            val outfitRepo =
                OutfitLogRepository(
                    outfitDao = outfitDao,
                    clothingDao = clothingDao,
                    ioDispatcher = dispatcher,
                    imageDeleter = {},
                    appScope = this,
                    trainingCoordinator = coordinator,
                )
            val wardrobeRepo =
                WardrobeRepository(
                    clothingDao = clothingDao,
                    outfitDao = outfitDao,
                    ioDispatcher = dispatcher,
                    imageDeleter = {},
                    trainingCoordinator = coordinator,
                )

            // 1. Outfit edit retrains.
            outfitRepo.updateOutfit(entryWithPieces.entry.copy(surveyFeelingScore = 5), listOf(piece))
            advanceUntilIdle()
            assertEquals(1L, coordinator.dataRevision.value)
            assertNotNull(outfitDao.getModelState())

            // 2. Non-warmth clothing edit (name only) does NOT retrain.
            val renamed = piece.copy(name = "Renamed tee")
            wardrobeRepo.updateClothing(renamed)
            advanceUntilIdle()
            assertEquals(1L, coordinator.dataRevision.value)

            // 3. Material edit on worn piece DOES retrain (Gate A).
            wardrobeRepo.updateClothing(renamed.copy(material = Material.WOOL))
            advanceUntilIdle()
            assertEquals(2L, coordinator.dataRevision.value)
            assertEquals(2L, outfitDao.getModelState()?.dataRevision)

            // 4. Deleting the last usable entry clears ModelState cleanly.
            outfitRepo.deleteEntry(entryWithPieces.entry)
            advanceUntilIdle()
            assertEquals(3L, coordinator.dataRevision.value)
            assertNull(outfitDao.getModelState())
            assertNull(outfitDao.observeModelState().first())
            assertTrue(outfitDao.modelStates.isEmpty())
        }
}
