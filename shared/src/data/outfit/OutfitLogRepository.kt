package nl.petrichor.app.data.outfit

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import nl.petrichor.app.data.database.defaultClothingDao
import nl.petrichor.app.data.database.defaultOutfitDao
import nl.petrichor.app.data.media.MediaStorage
import nl.petrichor.app.data.recommendation.RecommendationTrainingCoordinator
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
 * Repository for the outfit journal. Business boundary around [OutfitDao]:
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
    trainingCoordinator: RecommendationTrainingCoordinator? = null,
) {
    private val coordinator: RecommendationTrainingCoordinator by lazy {
        trainingCoordinator ?: RecommendationTrainingCoordinator(
            outfitDao = outfitDao,
            clothingDao = clothingDao,
            ioDispatcher = ioDispatcher,
            appScope = appScope,
        )
    }

    val entries: Flow<List<OutfitEntryWithPieces>> = outfitDao.getAll()

    val surveyedCount: Flow<Int> = outfitDao.countSurveyed()

    /** Count of surveyed entries with weather temperature and at least one linked garment. */
    val usableTrainingCount: Flow<Int> = outfitDao.countUsableTrainingEntries()

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
            val entryId =
                outfitDao.insertEntryWithPieces(
                    stamped,
                    pieces.mapIndexed { index, piece -> OutfitEntryPiece(entryId = 0, pieceId = piece.id, orderIndex = index) },
                )
            val latestUnlinked = outfitDao.getImpressionsForDate(stamped.wornOnDate).firstOrNull { it.wornEntryId == null }
            if (latestUnlinked != null) {
                outfitDao.linkImpressionWornEntry(latestUnlinked.id, entryId)
            }
            entryId
        }

    suspend fun updateOutfit(
        entry: OutfitEntry,
        pieces: List<ClothingPiece>,
    ) = withContext(ioDispatcher) {
        val previous = outfitDao.getById(entry.id)?.entry
        outfitDao.updateEntry(entry)
        outfitDao.clearEntryPieces(entry.id)
        outfitDao.insertEntryPieces(
            pieces.mapIndexed { index, piece -> OutfitEntryPiece(entryId = entry.id, pieceId = piece.id, orderIndex = index) },
        )
        if (entry.surveyFeelingScore != null || previous?.surveyFeelingScore != null) {
            coordinator.notifyDataChanged()
        }
    }

    suspend fun saveSurvey(
        entryId: Long,
        score: Int,
        verdict: WarmthVerdict,
        context: SurveyContextAnswers = SurveyContextAnswers(),
    ) = withContext(ioDispatcher) {
        outfitDao.setSurvey(
            entryId = entryId,
            score = score,
            verdict = verdict,
            surveyedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
            mostlyIndoors = context.mostlyIndoors,
            highActivity = context.highActivity,
            gotWet = context.gotWet,
        )
        coordinator.notifyDataChanged()
    }

    suspend fun recordRecommendationImpression(impression: RecommendationImpression): Long =
        withContext(ioDispatcher) {
            outfitDao.insertRecommendationImpression(impression)
        }

    suspend fun getImpressionsForDate(targetDate: LocalDate): List<RecommendationImpression> =
        withContext(ioDispatcher) {
            outfitDao.getImpressionsForDate(targetDate)
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
            coordinator.notifyDataChanged() // deleting an entry may shrink or empty training data
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
     * Retrains lazily at app start when the stored model is missing, stale, or mismatched.
     * Call once from the app shell.
     */
    fun ensureModelFresh() {
        coordinator.ensureModelFresh()
    }

    companion object {
        fun laundryUntilEpochMs(
            days: Int,
            nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
        ): Long = nowEpochMs + days * DAY_MS
    }
}
