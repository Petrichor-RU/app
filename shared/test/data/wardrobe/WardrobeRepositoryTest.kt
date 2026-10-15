package nl.petrichor.app.data.wardrobe

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WardrobeRepositoryTest {
    @Test
    fun `adding a new item supplies a creation timestamp`() = runBlocking {
        val dao = FakeClothingDao()
        val repository = WardrobeRepository(dao, kotlinx.coroutines.Dispatchers.Unconfined)
        val clothing = clothing(createdAt = 0L)

        repository.addClothing(clothing)

        assertEquals(clothing.copy(createdAt = 0L), dao.inserted.single().copy(createdAt = 0L))
        assertTrue(dao.inserted.single().createdAt > 0L)
    }

    @Test
    fun `adding an existing item preserves its creation timestamp`() = runBlocking {
        val dao = FakeClothingDao()
        val repository = WardrobeRepository(dao, kotlinx.coroutines.Dispatchers.Unconfined)
        val clothing = clothing(createdAt = 1234L)

        repository.addClothing(clothing)

        assertEquals(clothing, dao.inserted.single())
    }

    @Test
    fun `getting clothing types seeds built-ins before reading`() = runBlocking {
        val dao = FakeClothingDao()
        val repository = WardrobeRepository(dao, kotlinx.coroutines.Dispatchers.Unconfined)

        val types = repository.getClothingTypes().first()

        assertEquals(BuiltInClothingTypes.all, dao.insertedTypes.single())
        assertEquals(dao.types.value, types)
    }

    @Test
    fun `repository delegates reads and mutations to the dao`() = runBlocking {
        val dao = FakeClothingDao()
        val repository = WardrobeRepository(dao, kotlinx.coroutines.Dispatchers.Unconfined)
        val clothing = clothing(createdAt = 1234L)

        assertEquals(dao.clothing.value, repository.getAllClothing().first())
        repository.updateClothing(clothing)
        repository.deleteClothing(clothing)

        assertEquals(clothing, dao.updated.single())
        assertEquals(clothing, dao.deleted.single())
    }

    private fun clothing(createdAt: Long) = ClothingPiece(
        name = "Blue shirt",
        typeId = 1L,
        material = Material.COTTON,
        notes = "For testing",
        imagePath = "image.jpg",
        thumbnailPath = "thumbnail.jpg",
        createdAt = createdAt
    )
}

private class FakeClothingDao : ClothingDao {
    val clothing = MutableStateFlow(emptyList<ClothingPiece>())
    val types = MutableStateFlow(BuiltInClothingTypes.all)
    val inserted = mutableListOf<ClothingPiece>()
    val updated = mutableListOf<ClothingPiece>()
    val deleted = mutableListOf<ClothingPiece>()
    val insertedTypes = mutableListOf<List<ClothingType>>()

    override fun getAll(): Flow<List<ClothingPiece>> = clothing

    override fun getByType(typeId: Long): Flow<List<ClothingPiece>> =
        MutableStateFlow(clothing.value.filter { it.typeId == typeId })

    override suspend fun getById(id: Long): ClothingPiece? =
        clothing.value.firstOrNull { it.id == id }

    override suspend fun insert(clothing: ClothingPiece): Long {
        inserted += clothing
        return if (clothing.id == 0L) 1L else clothing.id
    }

    override suspend fun update(clothing: ClothingPiece) {
        updated += clothing
    }

    override suspend fun delete(clothing: ClothingPiece) {
        deleted += clothing
    }

    override suspend fun deleteAll() = Unit

    override fun getTypes(): Flow<List<ClothingType>> = types

    override suspend fun insertTypes(types: List<ClothingType>) {
        insertedTypes += types
    }

    override suspend fun insertType(type: ClothingType): Long = type.id
}
