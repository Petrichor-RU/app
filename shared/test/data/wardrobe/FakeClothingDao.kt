package nl.petrichor.app.data.wardrobe

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** MutableStateFlow-backed fake so tests need no Room driver (rule in AGENTS.md). */
class FakeClothingDao : ClothingDao {
    val clothing = MutableStateFlow(emptyList<ClothingPiece>())
    val types = MutableStateFlow(BuiltInClothingTypes.all)
    val inserted = mutableListOf<ClothingPiece>()
    val updated = mutableListOf<ClothingPiece>()
    val deleted = mutableListOf<ClothingPiece>()
    val insertedTypes = mutableListOf<List<ClothingType>>()
    val archivedMarks = mutableListOf<Pair<Long, Boolean>>()
    val laundryMarks = mutableListOf<Pair<Long, Long>>()

    override fun getAll(): Flow<List<ClothingPiece>> = clothing

    override fun getByType(typeId: Long): Flow<List<ClothingPiece>> =
        MutableStateFlow(clothing.value.filter { it.typeId == typeId && !it.archived })

    override suspend fun getById(id: Long): ClothingPiece? = clothing.value.firstOrNull { it.id == id && !it.archived }

    override suspend fun getByIdIncludingArchived(id: Long): ClothingPiece? = clothing.value.firstOrNull { it.id == id }

    override suspend fun setArchived(
        id: Long,
        archived: Boolean,
    ) {
        archivedMarks += id to archived
    }

    override suspend fun setLaundryUntil(
        id: Long,
        untilEpochMs: Long,
    ) {
        laundryMarks += id to untilEpochMs
    }

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
