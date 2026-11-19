package nl.petrichor.app.data.recommendation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.database.defaultClothingDao
import nl.petrichor.app.data.database.defaultOutfitDao
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitDao
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.wardrobe.ClothingDao
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.time.Clock

internal val recommendationAppScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Snapshot passed to the active model trainer for one training pass.
 */
data class TrainingSnapshot(
    val entries: List<OutfitEntryWithPieces>,
    val clothingTypes: List<ClothingType>,
    val dataRevision: Long,
    val trainedAtEpochMs: Long,
)

/**
 * Default Phase 1 trainer using [WarmthEncoding] + [LinearRegression].
 */
internal object LegacyLinearModelTrainer {
    fun train(snapshot: TrainingSnapshot): ModelState? {
        val typeById = snapshot.clothingTypes.associateBy { it.id }
        val samples =
            snapshot.entries.mapNotNull { entryWithPieces ->
                val entry = entryWithPieces.entry
                val tempC = entryTemperature(entry)
                val score = entry.surveyFeelingScore
                if (entryWithPieces.hasKnownPieces && tempC != null && score != null) {
                    WarmthEncoding.featureVector(tempC, entry.isRainy, entryWithPieces.pieces, typeById) to score.toDouble()
                } else {
                    null
                }
            }
        if (samples.isEmpty()) return null
        val coefficients = LinearRegression.fit(samples)
        return ModelState(
            coefficientsJson = Json.encodeToString(coefficients.toList()),
            trainedAtEpochMs = snapshot.trainedAtEpochMs,
            sampleCount = samples.size,
            schemaVersion = ModelState.LEGACY_FEATURE_SCHEMA_VERSION,
            dataRevision = snapshot.dataRevision,
        )
    }
}

/**
 * Single-flight, coalesced coordinator for recommendation model retraining.
 * Concurrent triggers (surveys, outfit edits, deletions, clothing metadata corrections)
 * coalesce via a conflated channel and never overwrite newer revisions with stale results.
 */
class RecommendationTrainingCoordinator(
    private val outfitDao: OutfitDao = defaultOutfitDao(),
    private val clothingDao: ClothingDao = defaultClothingDao(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val appScope: CoroutineScope = recommendationAppScope,
    private val clock: Clock = Clock.System,
    private val activeSchemaVersion: Int = ModelState.LEGACY_FEATURE_SCHEMA_VERSION,
    private val trainer: suspend (TrainingSnapshot) -> ModelState? = LegacyLinearModelTrainer::train,
) {
    private val triggerChannel = Channel<Unit>(Channel.CONFLATED)
    private val trainingMutex = Mutex()
    private val _dataRevision = MutableStateFlow(0L)

    val dataRevision: StateFlow<Long> = _dataRevision.asStateFlow()

    /** Increments the monotonic revision counter and schedules a coalesced retrain. */
    fun notifyDataChanged() {
        _dataRevision.update { it + 1 }
        triggerChannel.trySend(Unit)
        appScope.launch(ioDispatcher) { drainPendingTraining() }
    }

    /**
     * Checks whether the persisted model is missing, uses an outdated schema, predates the
     * newest survey, or disagrees with usable entry count (including when all usable entries
     * were deleted) and triggers a retrain/clear if needed.
     */
    fun ensureModelFresh() {
        appScope.launch(ioDispatcher) {
            val state = outfitDao.getModelState()
            val newestSurvey = outfitDao.getNewestSurveyEpochMs()
            val usableCount = outfitDao.countUsableTrainingEntries().first()
            val needsUpdate =
                when {
                    usableCount == 0 -> state != null
                    state == null -> true
                    state.schemaVersion != activeSchemaVersion -> true
                    state.sampleCount != usableCount -> true
                    newestSurvey != null && state.trainedAtEpochMs < newestSurvey -> true
                    else -> false
                }
            if (needsUpdate) {
                notifyDataChanged()
            }
        }
    }

    private suspend fun drainPendingTraining() {
        trainingMutex.withLock {
            while (triggerChannel.tryReceive().isSuccess) {
                runSingleTrainingPass()
            }
        }
    }

    private suspend fun runSingleTrainingPass() {
        val capturedRevision = _dataRevision.value
        val snapshot =
            withContext(ioDispatcher) {
                TrainingSnapshot(
                    entries = outfitDao.getTrainingEntries(),
                    clothingTypes = clothingDao.getTypes().first(),
                    dataRevision = capturedRevision,
                    trainedAtEpochMs = clock.now().toEpochMilliseconds(),
                )
            }
        val nextState = withContext(ioDispatcher) { trainer(snapshot) }
        // Only publish if no newer data change arrived while reading/training.
        if (_dataRevision.value == capturedRevision) {
            withContext(ioDispatcher) {
                if (nextState == null) {
                    outfitDao.deleteModelState()
                } else {
                    outfitDao.insertModelState(nextState)
                }
            }
        }
    }
}

/** Shared production coordinator instance so wardrobe and journal mutations coalesce together. */
internal object SharedTrainingCoordinator {
    val instance: RecommendationTrainingCoordinator by lazy { RecommendationTrainingCoordinator() }
}
