package nl.petrichor.app.data.outfit

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate

/** MutableStateFlow-backed fake so tests need no Room driver (same pattern as FakeClothingDao). */
class FakeOutfitDao : OutfitDao {
    val entries = MutableStateFlow<List<OutfitEntryWithPieces>>(emptyList())
    val modelStates = mutableListOf<ModelState>()
    val insertedEntries = mutableListOf<OutfitEntry>()
    val insertedLinks = mutableListOf<List<OutfitEntryPiece>>()
    val updatedEntries = mutableListOf<OutfitEntry>()
    val deletedEntries = mutableListOf<OutfitEntry>()
    val surveys = mutableListOf<Triple<Long, Int, WarmthVerdict>>()
    val laundryMarks = mutableListOf<Pair<Long, Long>>()
    val clearedPieceLinks = mutableListOf<Long>()
    val links = mutableListOf<OutfitEntryPiece>()
    var nextId = 1L

    override fun getAll(): Flow<List<OutfitEntryWithPieces>> = entries

    override fun getForDate(date: LocalDate): Flow<List<OutfitEntryWithPieces>> =
        MutableStateFlow(entries.value.filter { it.entry.wornOnDate == date })

    override suspend fun getUnsurveyedForDate(date: LocalDate): List<OutfitEntryWithPieces> =
        entries.value
            .filter { it.entry.wornOnDate == date && it.entry.surveyFeelingScore == null }
            .sortedByDescending { it.entry.createdAtEpochMs }

    override suspend fun getById(id: Long): OutfitEntryWithPieces? = entries.value.firstOrNull { it.entry.id == id }

    override suspend fun getEntriesForPiece(pieceId: Long): List<OutfitEntry> =
        entries.value
            .filter { entryWithPieces -> links.any { it.entryId == entryWithPieces.entry.id && it.pieceId == pieceId } }
            .map { it.entry }

    override fun countSurveyed(): Flow<Int> = MutableStateFlow(entries.value.count { it.entry.surveyFeelingScore != null })

    override suspend fun getTrainingEntries(): List<OutfitEntryWithPieces> =
        entries.value.filter {
            it.entry.surveyFeelingScore != null && (it.entry.minTempC != null || it.entry.temperatureC != null)
        }

    override suspend fun getNewestSurveyEpochMs(): Long? = entries.value.mapNotNull { it.entry.surveyedAtEpochMs }.maxOrNull()

    override suspend fun insertEntry(entry: OutfitEntry): Long {
        insertedEntries += entry
        val id = if (entry.id == 0L) nextId++ else entry.id
        entries.value = entries.value + OutfitEntryWithPieces(entry.copy(id = id), emptyList())
        return id
    }

    override suspend fun insertEntryPieces(links: List<OutfitEntryPiece>) {
        insertedLinks += links
        this.links += links
    }

    override suspend fun updateEntry(entry: OutfitEntry) {
        updatedEntries += entry
        entries.value = entries.value.map { if (it.entry.id == entry.id) it.copy(entry = entry) else it }
    }

    override suspend fun deleteEntry(entry: OutfitEntry) {
        deletedEntries += entry
        entries.value = entries.value.filterNot { it.entry.id == entry.id }
    }

    override suspend fun setSurvey(
        entryId: Long,
        score: Int,
        verdict: WarmthVerdict,
        surveyedAtEpochMs: Long,
    ) {
        surveys += Triple(entryId, score, verdict)
        entries.value =
            entries.value.map {
                if (it.entry.id == entryId) {
                    it.copy(
                        entry =
                            it.entry.copy(
                                surveyFeelingScore = score,
                                surveyWarmthVerdict = verdict,
                                surveyedAtEpochMs = surveyedAtEpochMs,
                            ),
                    )
                } else {
                    it
                }
            }
    }

    override suspend fun setLaundryUntil(
        id: Long,
        untilEpochMs: Long,
    ) {
        laundryMarks += id to untilEpochMs
    }

    override suspend fun clearEntryPieces(entryId: Long) {
        clearedPieceLinks += entryId
        links.removeAll { it.entryId == entryId }
    }

    override suspend fun getModelState(): ModelState? = modelStates.lastOrNull()

    override fun observeModelState(): Flow<ModelState?> = MutableStateFlow(modelStates.lastOrNull())

    override suspend fun insertModelState(state: ModelState) {
        modelStates += state
    }
}
