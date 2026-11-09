package nl.petrichor.app.data.wardrobe

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private class InMemoryClothingDao : ClothingDao {
    private val mutex = Mutex()
    private val clothing = MutableStateFlow(emptyList<ClothingPiece>())
    private val types = MutableStateFlow(emptyList<ClothingType>())

    override fun getAll(): Flow<List<ClothingPiece>> =
        clothing.map { items -> items.sortedByDescending { it.createdAt } }

    override fun getByType(typeId: Long): Flow<List<ClothingPiece>> =
        clothing.map { items -> items.filter { it.typeId == typeId }.sortedByDescending { it.createdAt } }

    override suspend fun getById(id: Long): ClothingPiece? =
        mutex.withLock { clothing.value.firstOrNull { it.id == id } }

    override suspend fun insert(clothing: ClothingPiece): Long = mutex.withLock {
        val id = clothing.id.takeUnless { it == 0L } ?: nextId(this@InMemoryClothingDao.clothing.value)
        val item = clothing.copy(id = id)
        this@InMemoryClothingDao.clothing.value =
            this@InMemoryClothingDao.clothing.value.filterNot { it.id == id } + item
        id
    }

    override suspend fun update(clothing: ClothingPiece) = mutex.withLock {
        this@InMemoryClothingDao.clothing.value =
            this@InMemoryClothingDao.clothing.value.map { if (it.id == clothing.id) clothing else it }
    }

    override suspend fun delete(clothing: ClothingPiece) = mutex.withLock {
        this@InMemoryClothingDao.clothing.value =
            this@InMemoryClothingDao.clothing.value.filterNot { it.id == clothing.id }
    }

    override suspend fun deleteAll() = mutex.withLock {
        clothing.value = emptyList()
    }

    override fun getTypes(): Flow<List<ClothingType>> =
        types.map { values -> values.sortedWith(compareBy<ClothingType> { it.supertype }.thenBy { it.name }) }

    override suspend fun insertTypes(types: List<ClothingType>) = mutex.withLock {
        val values = this@InMemoryClothingDao.types.value.toMutableList()
        types.forEach { candidate ->
            if (values.none { it.supertype == candidate.supertype && it.name == candidate.name }) {
                val id = candidate.id.takeUnless { it == 0L } ?: nextId(values)
                values += candidate.copy(id = id)
            }
        }
        this@InMemoryClothingDao.types.value = values
    }

    override suspend fun insertType(type: ClothingType): Long = mutex.withLock {
        val id = type.id.takeUnless { it == 0L } ?: nextId(types.value)
        this@InMemoryClothingDao.types.value += type.copy(id = id)
        id
    }

    private fun nextId(items: List<ClothingPiece>): Long =
        (items.maxOfOrNull { it.id } ?: 0L) + 1L

    private fun nextId(items: List<ClothingType>): Long =
        (items.maxOfOrNull { it.id } ?: 0L) + 1L
}

private val inMemoryClothingDao = InMemoryClothingDao()

actual fun defaultClothingDao(): ClothingDao = inMemoryClothingDao
