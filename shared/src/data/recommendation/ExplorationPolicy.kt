package nl.petrichor.app.data.recommendation

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import nl.petrichor.app.data.outfit.RecommendationImpression
import kotlin.random.Random
import kotlin.time.Clock

/** One recorded slot inside [RecommendationImpression.candidatesJson] for offline IPS evaluation. */
@Serializable
data class CandidateImpressionSlot(
    val slotIndex: Int,
    val pieceIds: List<Long>,
    val source: RecommendationSource,
    val isExploration: Boolean,
    val propensity: Double,
    val predictedRating: Double,
    val pTooCold: Double,
    val pPerfect: Double,
    val pTooWarm: Double,
    val ratingEpistemicStd: Double,
    val thermalEpistemicStd: Double,
)

/** Ranked recommendation slate paired with per-slot metadata for logging and testing. */
data class RankedSlate(
    val candidates: List<OutfitCandidate>,
    val slots: List<CandidateImpressionSlot>,
    val policyName: String,
    val isExplorationSlate: Boolean,
)

internal data class EvaluatedCandidate(
    val candidate: OutfitCandidate,
    val baseScore: Double,
    val tempPenalty: Double,
    val raw: RawOutfitSignals?,
    val features: DoubleArray?,
    val unseenPieceIds: List<Long>,
    val comfort: ComfortPrediction?,
)

private sealed interface CandidateDedupKey {
    data class PieceSet(
        val sortedPieceIds: List<Long>,
    ) : CandidateDedupKey

    data class SelfieOnly(
        val entryId: Long,
    ) : CandidateDedupKey

    data class SuggestionOnly(
        val text: String?,
    ) : CandidateDedupKey
}

/**
 * Ranks evaluated candidates into a conservative 3-slot slate with constrained Thompson
 * sampling on Slot 3 via [ThompsonSampler].
 */
internal object ExplorationPolicy {
    const val POLICY_CONSTRAINED_THOMPSON = "CONSTRAINED_THOMPSON_V1"
    const val POLICY_GREEDY = "BAYESIAN_GREEDY_V1"
    const val POLICY_HEURISTIC = "HEURISTIC_V1"
    const val POLICY_FALLBACK = "FALLBACK_V1"

    const val EXPLORATION_MIN_STD = 0.20
    const val SAFE_MIN_P_PERFECT = 0.25
    const val SAFE_MAX_EXTREME_DISCOMFORT = 0.55
    const val LOWER_BOUND_Z = 1.0
    const val SAFE_MIN_LOWER_RATING = 2.2

    private val evaluatedRankOrder =
        Comparator<EvaluatedCandidate> { left, right ->
            val scoreCmp = right.baseScore.compareTo(left.baseScore)
            if (scoreCmp != 0) return@Comparator scoreCmp
            val loggedCmp = (left.candidate.loggedEntry == null).compareTo(right.candidate.loggedEntry == null)
            if (loggedCmp != 0) return@Comparator loggedCmp
            val leftEntry = left.candidate.loggedEntry?.entry
            val rightEntry = right.candidate.loggedEntry?.entry
            val surveyCmp = (rightEntry?.surveyFeelingScore ?: Int.MIN_VALUE).compareTo(leftEntry?.surveyFeelingScore ?: Int.MIN_VALUE)
            if (surveyCmp != 0) return@Comparator surveyCmp
            val dateCmp = compareValues(rightEntry?.wornOnDate, leftEntry?.wornOnDate)
            if (dateCmp != 0) return@Comparator dateCmp
            (rightEntry?.createdAtEpochMs ?: Long.MIN_VALUE).compareTo(leftEntry?.createdAtEpochMs ?: Long.MIN_VALUE)
        }

    fun sessionRandom(
        date: LocalDate,
        modelDataRevision: Long,
    ): Random {
        val dateMix = date.toEpochDays() * 6364136223846793005L
        val revMix = modelDataRevision * 1442695040888963407L
        val seed = dateMix xor (revMix + -7046029254386353131L)
        return Random(seed)
    }

    fun resolvePolicyName(mode: RecommendationMode): String =
        when (mode) {
            RecommendationMode.MODEL -> POLICY_GREEDY
            RecommendationMode.HEURISTIC -> POLICY_HEURISTIC
            RecommendationMode.FALLBACK -> POLICY_FALLBACK
        }

    fun rankAndSelectSlate(
        context: RecommendationScoringContext,
        evaluated: List<EvaluatedCandidate>,
    ): RankedSlate {
        val dedupedSorted =
            evaluated
                .sortedWith(evaluatedRankOrder)
                .distinctBy { dedupKey(it.candidate) }
        val sessionRandom =
            if (context.input.random === Random.Default) {
                sessionRandom(context.input.date, context.input.modelState?.dataRevision ?: 0L)
            } else {
                context.input.random
            }
        return selectSlate(context, dedupedSorted, sessionRandom)
    }

    fun selectSlate(
        context: RecommendationScoringContext,
        dedupedSorted: List<EvaluatedCandidate>,
        random: Random,
    ): RankedSlate {
        val model = context.decodedModel
        val modelReady =
            context.mode == RecommendationMode.MODEL &&
                model != null &&
                context.input.tempC != null &&
                dedupedSorted.size >= RecommendationEngine.MAX_CANDIDATES
        if (!modelReady) {
            val top = dedupedSorted.take(RecommendationEngine.MAX_CANDIDATES)
            return RankedSlate(
                candidates = top.map { it.candidate },
                slots = top.mapIndexed { idx, eval -> toImpressionSlot(idx, eval, isExploration = false, propensity = 1.0) },
                policyName = resolvePolicyName(context.mode),
                isExplorationSlate = false,
            )
        }

        val slot1 = dedupedSorted[0]
        val slot2 = dedupedSorted[1]
        val remaining = dedupedSorted.drop(2)
        val safePool = remaining.filter { isSafeExplorationCandidate(it) }
        val hasMeaningfulUncertainty =
            safePool.any { eval ->
                val comfort = eval.comfort!!
                maxOf(comfort.rating.epistemicStd, comfort.thermal.epistemicStd) >= EXPLORATION_MIN_STD
            }
        return if (!hasMeaningfulUncertainty) {
            val greedyTop = listOf(slot1, slot2, remaining.first())
            RankedSlate(
                candidates = greedyTop.map { it.candidate },
                slots = greedyTop.mapIndexed { idx, eval -> toImpressionSlot(idx, eval, isExploration = false, propensity = 1.0) },
                policyName = POLICY_GREEDY,
                isExplorationSlate = false,
            )
        } else {
            val (exploredSlot, propensity) = ThompsonSampler.sampleSafeSlot3(model, safePool, random)
            val chosenList = listOf(slot1, slot2, exploredSlot)
            RankedSlate(
                candidates = chosenList.map { it.candidate },
                slots =
                    listOf(
                        toImpressionSlot(0, slot1, isExploration = false, propensity = 1.0),
                        toImpressionSlot(1, slot2, isExploration = false, propensity = 1.0),
                        toImpressionSlot(2, exploredSlot, isExploration = true, propensity = propensity),
                    ),
                policyName = POLICY_CONSTRAINED_THOMPSON,
                isExplorationSlate = true,
            )
        }
    }

    internal fun isSafeExplorationCandidate(eval: EvaluatedCandidate): Boolean {
        val comfort = eval.comfort
        val eligible = comfort != null && eval.candidate.pieces.isNotEmpty()
        return if (!eligible) {
            false
        } else {
            val ratingLowerBound = comfort.rating.mean - LOWER_BOUND_Z * comfort.rating.epistemicStd
            comfort.thermal.perfect >= SAFE_MIN_P_PERFECT &&
                comfort.thermal.tooCold <= SAFE_MAX_EXTREME_DISCOMFORT &&
                comfort.thermal.tooWarm <= SAFE_MAX_EXTREME_DISCOMFORT &&
                ratingLowerBound >= SAFE_MIN_LOWER_RATING
        }
    }

    private fun dedupKey(candidate: OutfitCandidate): CandidateDedupKey =
        when {
            candidate.pieces.isNotEmpty() -> CandidateDedupKey.PieceSet(candidate.pieces.map { it.id }.sorted())
            candidate.loggedEntry != null -> CandidateDedupKey.SelfieOnly(candidate.loggedEntry.entry.id)
            else -> CandidateDedupKey.SuggestionOnly(candidate.generalSuggestion)
        }

    private fun toImpressionSlot(
        slotIndex: Int,
        eval: EvaluatedCandidate,
        isExploration: Boolean,
        propensity: Double,
    ): CandidateImpressionSlot {
        val comfort = eval.comfort
        return CandidateImpressionSlot(
            slotIndex = slotIndex,
            pieceIds = eval.candidate.pieces.map { it.id },
            source = eval.candidate.source,
            isExploration = isExploration,
            propensity = propensity,
            predictedRating = comfort?.rating?.mean ?: eval.baseScore,
            pTooCold = comfort?.thermal?.tooCold ?: 0.0,
            pPerfect = comfort?.thermal?.perfect ?: 0.0,
            pTooWarm = comfort?.thermal?.tooWarm ?: 0.0,
            ratingEpistemicStd = comfort?.rating?.epistemicStd ?: 0.0,
            thermalEpistemicStd = comfort?.thermal?.epistemicStd ?: 0.0,
        )
    }

    fun buildImpressionEntity(
        slate: RankedSlate,
        input: RecommendationInput,
        locationName: String?,
        candidatesJson: String,
        shownAtEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    ): RecommendationImpression =
        RecommendationImpression(
            shownAtEpochMs = shownAtEpochMs,
            targetDate = input.date,
            locationName = locationName,
            tempC = input.tempC,
            isRainy = input.isRainy,
            windSpeedMs = input.windSpeedMs,
            modelSchemaVersion = input.modelState?.schemaVersion ?: 0,
            modelDataRevision = input.modelState?.dataRevision ?: 0L,
            policyName = slate.policyName,
            candidatesJson = candidatesJson,
        )
}
