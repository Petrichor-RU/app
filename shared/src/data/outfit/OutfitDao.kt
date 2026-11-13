package nl.petrichor.app.data.outfit

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
@Suppress("TooManyFunctions") // DAO query surface for the journal; each method is a single query
interface OutfitDao {
    @Transaction
    @Query("SELECT * FROM outfit_entries ORDER BY wornOnDate DESC, createdAtEpochMs DESC")
    fun getAll(): Flow<List<OutfitEntryWithPieces>>

    @Transaction
    @Query("SELECT * FROM outfit_entries WHERE wornOnDate = :date ORDER BY createdAtEpochMs DESC")
    fun getForDate(date: LocalDate): Flow<List<OutfitEntryWithPieces>>

    @Transaction
    @Query(
        "SELECT * FROM outfit_entries WHERE wornOnDate = :date AND surveyFeelingScore IS NULL " +
            "ORDER BY createdAtEpochMs DESC",
    )
    suspend fun getUnsurveyedForDate(date: LocalDate): List<OutfitEntryWithPieces>

    @Transaction
    @Query("SELECT * FROM outfit_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): OutfitEntryWithPieces?

    @Query(
        "SELECT outfit_entries.* FROM outfit_entries " +
            "INNER JOIN outfit_entry_pieces ON outfit_entries.id = outfit_entry_pieces.entryId " +
            "WHERE outfit_entry_pieces.pieceId = :pieceId",
    )
    suspend fun getEntriesForPiece(pieceId: Long): List<OutfitEntry>

    @Query("SELECT COUNT(*) FROM outfit_entries WHERE surveyFeelingScore IS NOT NULL")
    fun countSurveyed(): Flow<Int>

    @Transaction
    @Query(
        "SELECT * FROM outfit_entries WHERE surveyFeelingScore IS NOT NULL " +
            "AND (minTempC IS NOT NULL OR temperatureC IS NOT NULL) ORDER BY surveyedAtEpochMs ASC",
    )
    suspend fun getTrainingEntries(): List<OutfitEntryWithPieces>

    @Query("SELECT MAX(surveyedAtEpochMs) FROM outfit_entries WHERE surveyedAtEpochMs IS NOT NULL")
    suspend fun getNewestSurveyEpochMs(): Long?

    @Insert
    suspend fun insertEntry(entry: OutfitEntry): Long

    @Insert
    suspend fun insertEntryPieces(links: List<OutfitEntryPiece>)

    @Transaction
    suspend fun insertEntryWithPieces(
        entry: OutfitEntry,
        pieces: List<OutfitEntryPiece>,
    ): Long {
        val entryId = insertEntry(entry)
        insertEntryPieces(pieces.map { it.copy(entryId = entryId) })
        return entryId
    }

    @Update
    suspend fun updateEntry(entry: OutfitEntry)

    @Delete
    suspend fun deleteEntry(entry: OutfitEntry)

    @Query(
        "UPDATE outfit_entries SET surveyFeelingScore = :score, surveyWarmthVerdict = :verdict, " +
            "surveyedAtEpochMs = :surveyedAtEpochMs WHERE id = :entryId",
    )
    suspend fun setSurvey(
        entryId: Long,
        score: Int,
        verdict: WarmthVerdict,
        surveyedAtEpochMs: Long,
    )

    @Query("UPDATE outfit_entries SET laundryUntilEpochMs = :untilEpochMs WHERE id = :id")
    suspend fun setLaundryUntil(
        id: Long,
        untilEpochMs: Long,
    )

    @Query("DELETE FROM outfit_entry_pieces WHERE entryId = :entryId")
    suspend fun clearEntryPieces(entryId: Long)

    @Query("SELECT * FROM model_state WHERE id = 1 LIMIT 1")
    suspend fun getModelState(): ModelState?

    @Query("SELECT * FROM model_state WHERE id = 1 LIMIT 1")
    fun observeModelState(): Flow<ModelState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModelState(state: ModelState)
}
