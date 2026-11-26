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
    val previousState: ModelState? = null,
)

/**
 * Single-flight, coalesced coordinator for recommendation model retraining.
 * Concurrent triggers (surveys, outfit edits, deletions) coalesce via a conflated channel
 * and never overwrite newer revisions with stale results.
 */
class RecommendationTrainingCoordinator(
    private val outfitDao: OutfitDao = defaultOutfitDao(),
    private val clothingDao: ClothingDao = defaultClothingDao(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val appScope: CoroutineScope = recommendationAppScope,
    private val clock: Clock = Clock.System,
    private val trainer: suspend (TrainingSnapshot) -> ModelState? = BayesianModelTrainer::train,
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
     * were deleted) and triggers a retrain or clear if needed.
     */
    fun ensureModelFresh() {
        appScope.launch(ioDispatcher) {
            val state = outfitDao.getModelState()
            val newestSurvey = outfitDao.getNewestSurveyEpochMs()
            val usableCount = outfitDao.getTrainingEntries().count(BayesianModelTrainer::isUsableTrainingEntry)
            val decoded = DecodedModel.decode(state)
            val needsUpdate =
                when {
                    usableCount == 0 -> decoded != null
                    decoded == null -> true
                    state?.sampleCount != usableCount -> true
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
                    previousState = outfitDao.getModelState(),
                )
            }
        val nextState = withContext(ioDispatcher) { trainer(snapshot) }
        // Only publish if no newer data change arrived while reading/training.
        if (_dataRevision.value == capturedRevision) {
            withContext(ioDispatcher) {
                if (nextState != null) {
                    outfitDao.insertModelState(nextState)
                } else if (DecodedModel.decode(snapshot.previousState) != null) {
                    outfitDao.insertModelState(
                        ModelState(
                            coefficientsJson = "",
                            trainedAtEpochMs = snapshot.trainedAtEpochMs,
                            sampleCount = 0,
                        ),
                    )
                }
            }
        }
    }
}
