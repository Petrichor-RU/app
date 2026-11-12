package nl.petrichor.app.data.outfit

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.database.defaultClothingDao
import nl.petrichor.app.data.database.defaultOutfitDao
import nl.petrichor.app.data.media.MediaStorage
import nl.petrichor.app.data.recommendation.LinearRegression
import nl.petrichor.app.data.recommendation.WarmthEncoding
import nl.petrichor.app.data.wardrobe.ClothingDao
import nl.petrichor.app.data.wardrobe.ClothingPiece
import kotlin.time.Clock

/**
 * Application-level scope for background model retraining, owned by the repository layer
 * (consistent with the project's no-DI style).
 */
internal val outfitAppScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Repository for the outfit journal (plan section 3.4). Business boundary around [OutfitDao]:
 * stamps creation timestamps, computes laundry-until values, persists entries and piece links
 * in a transaction, and triggers background model retraining after survey changes.
 */
@Suppress("TooManyFunctions") // repository business boundary around the DAOs
class OutfitLogRepository(
    private val outfitDao: OutfitDao = defaultOutfitDao(),
    private val clothingDao: ClothingDao = defaultClothingDao(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val imageDeleter: suspend (String) -> Unit = { path -> MediaStorage.deleteImage(path) },
    private val appScope: CoroutineScope = outfitAppScope,
) {
    val entries: Flow<List<OutfitEntryWithPieces>> = outfitDao.getAll()

    val surveyedCount: Flow<Int> = outfitDao.countSurveyed()

    /** Trained warmth-model state (null until the first retrain); drives MODEL-mode recommendations. */
    val modelState: Flow<ModelState?> = outfitDao.observeModelState()

    private val _laundryVersion = MutableStateFlow(0)

    /** Bumped on every laundry mark so recommendation UIs can recompute. */
    val laundryVersion: StateFlow<Int> = _laundryVersion.asStateFlow()

    suspend fun logOutfit(
        entry: OutfitEntry,
        pieces: List<ClothingPiece>,
    ): Long =
        withContext(ioDispatcher) {
            val stamped =
                entry
                    .takeUnless { it.createdAtEpochMs != 0L }
                    ?.copy(createdAtEpochMs = Clock.System.now().toEpochMilliseconds())
                    ?: entry
            outfitDao.insertEntryWithPieces(
                stamped,
                pieces.mapIndexed { index, piece -> OutfitEntryPiece(entryId = 0, pieceId = piece.id, orderIndex = index) },
            )
        }

    suspend fun updateOutfit(
        entry: OutfitEntry,
        pieces: List<ClothingPiece>,
    ) = withContext(ioDispatcher) {
        outfitDao.updateEntry(entry)
        outfitDao.clearEntryPieces(entry.id)
        outfitDao.insertEntryPieces(
            pieces.mapIndexed { index, piece -> OutfitEntryPiece(entryId = entry.id, pieceId = piece.id, orderIndex = index) },
        )
    }

    suspend fun saveSurvey(
        entryId: Long,
        score: Int,
        verdict: WarmthVerdict,
    ) = withContext(ioDispatcher) {
        outfitDao.setSurvey(entryId, score, verdict, Clock.System.now().toEpochMilliseconds())
        scheduleRetrain()
    }

    suspend fun markOutfitLaundry(
        entryId: Long,
        days: Int,
    ) = withContext(ioDispatcher) {
        outfitDao.setLaundryUntil(entryId, laundryUntilEpochMs(days))
        _laundryVersion.update { it + 1 }
    }

    suspend fun deleteEntry(entry: OutfitEntry) =
        withContext(ioDispatcher) {
            outfitDao.deleteEntry(entry)
            entry.photoPath?.let { imageDeleter(it) }
            scheduleRetrain() // deleting an entry shrinks the training data
        }

    suspend fun mostRecentUnsurveyedToday(): OutfitEntryWithPieces? =
        mostRecentUnsurveyedForDate(Clock.System.todayIn(TimeZone.currentSystemDefault()))

    /** Most recent un-surveyed entry of [date] (decision 17: with multiple entries on one day, the survey targets the newest). */
    suspend fun mostRecentUnsurveyedForDate(date: LocalDate): OutfitEntryWithPieces? =
        withContext(ioDispatcher) {
            outfitDao.getUnsurveyedForDate(date).firstOrNull()
        }

    suspend fun getEntry(id: Long): OutfitEntryWithPieces? =
        withContext(ioDispatcher) {
            outfitDao.getById(id)
        }

    /**
     * Retrains lazily at app start when the stored model is older than the newest survey.
     * Call once from the app shell.
     */
    fun ensureModelFresh() {
        appScope.launch(ioDispatcher) {
            val state = outfitDao.getModelState()
            val newestSurvey = outfitDao.getNewestSurveyEpochMs()
            if (state == null || (newestSurvey != null && state.trainedAtEpochMs < newestSurvey)) {
                retrain()
            }
        }
    }

    private fun scheduleRetrain() {
        appScope.launch(ioDispatcher) { retrain() }
    }

    private suspend fun retrain() {
        val typeById = clothingDao.getTypes().first().associateBy { it.id }
        val samples =
            outfitDao.getTrainingEntries().mapNotNull { entryWithPieces ->
                val entry = entryWithPieces.entry
                val tempC =
                    when {
                        entry.minTempC != null && entry.maxTempC != null -> (entry.minTempC + entry.maxTempC) / 2
                        else -> entry.temperatureC
                    }
                val score = entry.surveyFeelingScore
                if (tempC != null && score != null) {
                    WarmthEncoding.featureVector(tempC, entry.isRainy, entryWithPieces.pieces, typeById) to score.toDouble()
                } else {
                    null
                }
            }
        if (samples.isEmpty()) return
        val coefficients = LinearRegression.fit(samples)
        outfitDao.insertModelState(
            ModelState(
                coefficientsJson = Json.encodeToString(coefficients.toList()),
                trainedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                sampleCount = samples.size,
            ),
        )
    }

    companion object {
        fun laundryUntilEpochMs(
            days: Int,
            nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
        ): Long = nowEpochMs + days * DAY_MS
    }
}
