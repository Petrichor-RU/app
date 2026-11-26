package nl.petrichor.app.data.wardrobe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.FakeOutfitDao
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryPiece
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WardrobeRepositoryTest {
    @Test
    fun `adding a new item supplies a creation timestamp`() =
        runTest {
            val dao = FakeClothingDao()
            val repository = WardrobeRepository(dao, FakeOutfitDao(), Dispatchers.Unconfined) {}
            val clothing = clothing(createdAt = 0L)

            repository.addClothing(clothing)

            assertEquals(clothing.copy(createdAt = 0L), dao.inserted.single().copy(createdAt = 0L))
            assertTrue(dao.inserted.single().createdAt > 0L)
        }

    @Test
    fun `adding an existing item preserves its creation timestamp`() =
        runTest {
            val dao = FakeClothingDao()
            val repository = WardrobeRepository(dao, FakeOutfitDao(), Dispatchers.Unconfined) {}
            val clothing = clothing(createdAt = 1234L)

            repository.addClothing(clothing)

            assertEquals(clothing, dao.inserted.single())
        }

    @Test
    fun `getting clothing types seeds built-ins before reading`() =
        runTest {
            val dao = FakeClothingDao()
            val repository = WardrobeRepository(dao, FakeOutfitDao(), Dispatchers.Unconfined) {}

            val types = repository.getClothingTypes().first()

            assertEquals(BuiltInClothingTypes.all, dao.insertedTypes.single())
            assertEquals(dao.types.value, types)
        }

    @Test
    fun `collecting clothing types again does not reseed built-ins`() =
        runTest {
            val dao = FakeClothingDao()
            val repository = WardrobeRepository(dao, FakeOutfitDao(), Dispatchers.Unconfined) {}

            repository.getClothingTypes().first()
            repository.getClothingTypes().first()

            assertEquals(1, dao.insertedTypes.size)
        }

    @Test
    fun `repository delegates reads and mutations to the dao`() =
        runTest {
            val dao = FakeClothingDao()
            val repository = WardrobeRepository(dao, FakeOutfitDao(), Dispatchers.Unconfined) {}
            val clothing = clothing(createdAt = 1234L)

            assertEquals(dao.clothing.value, repository.getAllClothing().first())
            repository.updateClothing(clothing)
            repository.deleteClothing(clothing)

            assertEquals(clothing, dao.updated.single())
            assertEquals(clothing, dao.deleted.single())
        }

    @Test
    fun `piece linked to a journal entry is archived and its image is kept`() =
        runTest {
            val dao = FakeClothingDao()
            val outfitDao = FakeOutfitDao()
            outfitDao.entries.value = listOf(outfitEntry(id = 1L))
            outfitDao.links += OutfitEntryPiece(entryId = 1L, pieceId = 7L)
            val deletedImages = mutableListOf<String>()
            val repository = WardrobeRepository(dao, outfitDao, Dispatchers.Unconfined) { deletedImages += it }
            val clothing = clothing(createdAt = 1234L).copy(id = 7L)

            val result = repository.deletePieceWithArchive(clothing)

            assertEquals(PieceDeleteResult.ARCHIVED, result)
            assertEquals(listOf(7L to true), dao.archivedMarks)
            assertTrue(dao.deleted.isEmpty())
            assertTrue(deletedImages.isEmpty())
        }

    @Test
    fun `unlinked piece is hard-deleted and its image is removed`() =
        runTest {
            val dao = FakeClothingDao()
            val outfitDao = FakeOutfitDao()
            val deletedImages = mutableListOf<String>()
            val repository = WardrobeRepository(dao, outfitDao, Dispatchers.Unconfined) { deletedImages += it }
            val clothing = clothing(createdAt = 1234L).copy(id = 9L)

            val result = repository.deletePieceWithArchive(clothing)

            assertEquals(PieceDeleteResult.DELETED, result)
            assertEquals(clothing, dao.deleted.single())
            assertTrue(dao.archivedMarks.isEmpty())
            assertEquals(listOf("image.jpg"), deletedImages)
        }

    @Test
    fun `laundry state derives from laundryUntilEpochMs`() {
        val repository = WardrobeRepository(FakeClothingDao(), FakeOutfitDao(), Dispatchers.Unconfined) {}
        val clean = clothing(createdAt = 1L).copy(laundryUntilEpochMs = 0L)
        val inLaundry = clothing(createdAt = 1L).copy(laundryUntilEpochMs = 1000L)

        assertFalse(repository.pieceIsInLaundry(clean, nowEpochMs = 500L))
        assertTrue(repository.pieceIsInLaundry(inLaundry, nowEpochMs = 500L))
        assertFalse(repository.pieceIsInLaundry(inLaundry, nowEpochMs = 1000L))
    }

    private fun clothing(createdAt: Long) =
        ClothingPiece(
            name = "Blue shirt",
            typeId = 1L,
            material = Material.COTTON,
            notes = "For testing",
            imagePath = "image.jpg",
            thumbnailPath = "thumbnail.jpg",
            createdAt = createdAt,
        )

    private fun outfitEntry(id: Long) =
        OutfitEntryWithPieces(
            OutfitEntry(
                id = id,
                wornOnDate = LocalDate(2026, 11, 12),
                photoPath = null,
                notes = null,
                temperatureC = null,
                feelsLikeC = null,
                minTempC = null,
                maxTempC = null,
                weatherDescription = null,
                weatherIcon = null,
                isRainy = false,
                surveyFeelingScore = null,
                surveyWarmthVerdict = null,
                surveyedAtEpochMs = null,
            ),
            emptyList(),
        )
}
