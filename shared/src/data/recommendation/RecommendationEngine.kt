package nl.petrichor.app.data.recommendation

import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.random.Random
import kotlin.time.Clock

/** Scoring mode derived from the amount of usable survey data. */
enum class RecommendationMode {
    MODEL,
    HEURISTIC,
    FALLBACK,
}

/** Where a candidate came from; mirrors the active [RecommendationMode]. */
enum class RecommendationSource {
    MODEL,
    HEURISTIC,
    FALLBACK,
}

/** One suggested outfit: either a logged journal entry (possibly with replacements) or a constructed combination. */
data class OutfitCandidate(
    val loggedEntry: OutfitEntryWithPieces?,
    val pieces: List<ClothingPiece>,
    val selfiePath: String?,
    val generalSuggestion: String?,
    val source: RecommendationSource,
)

/** A labeled display group of pieces. */
data class OutfitGroup(
    val zone: CoverageZone,
    val label: String,
    val pieces: List<ClothingPiece>,
)

data class RecommendationInput(
    val date: LocalDate,
    val today: LocalDate,
    val tempC: Double?,
    val isRainy: Boolean,
    val wardrobePieces: List<ClothingPiece>,
    val clothingTypes: List<ClothingType>,
    val entries: List<OutfitEntryWithPieces>,
    val surveyedCount: Int,
    val modelState: ModelState?,
    val nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    val random: Random = Random.Default,
)

internal data class RecommendationScoringContext(
    val input: RecommendationInput,
    val pool: List<ClothingPiece>,
    val typeById: Map<Long, ClothingType>,
    val mode: RecommendationMode,
    val decodedModel: DecodedModel?,
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
 * Zone-based recommendation engine: builds candidates from logged outfits
 * (with laundry replacements) and constructed combinations, scores them with the active mode
 * and returns the top few.
 */
object RecommendationEngine {
    const val MAX_CANDIDATES = 3
    const val MAX_LOGGED_CANDIDATES = 120
    const val MAX_CONSTRUCTED_CANDIDATES = 120
    const val MODEL_MIN_SURVEYED = 10
    private const val SCORING_YIELD_INTERVAL = 16

    /** Model mode needs >= 10 usable surveyed entries and a valid trained model; 1-9 is heuristic; none is fallback. */
    fun activeMode(
        surveyedCount: Int,
        hasTrainedModel: Boolean,
    ): RecommendationMode =
        when {
            surveyedCount >= MODEL_MIN_SURVEYED && hasTrainedModel -> RecommendationMode.MODEL
            surveyedCount >= 1 -> RecommendationMode.HEURISTIC
            else -> RecommendationMode.FALLBACK
        }

    fun recommendOutfits(input: RecommendationInput): List<OutfitCandidate> {
        val context = prepareContext(input)
        val candidates = CandidateGenerator.generate(context)
        val scored =
            candidates.map { candidate ->
                candidate to Scoring.scoreCandidate(input, candidate, context.typeById, context.mode, context.decodedModel)
            }
        return rankAndDeduplicate(scored)
    }

    /**
     * Suspending entry point for UI/background ranking: yields every [SCORING_YIELD_INTERVAL]
     * candidates so single-threaded runtimes such as `wasmJs` never stall UI frames.
     */
    suspend fun recommendOutfitsSuspending(input: RecommendationInput): List<OutfitCandidate> {
        val context = prepareContext(input)
        val candidates = CandidateGenerator.generate(context)
        val scored = ArrayList<Pair<OutfitCandidate, Double>>(candidates.size)
        candidates.forEachIndexed { index, candidate ->
            if (index > 0 && index % SCORING_YIELD_INTERVAL == 0) yield()
            val score = Scoring.scoreCandidate(input, candidate, context.typeById, context.mode, context.decodedModel)
            scored += candidate to score
        }
        return rankAndDeduplicate(scored)
    }

    private fun prepareContext(input: RecommendationInput): RecommendationScoringContext {
        val typeById = input.clothingTypes.associateBy { it.id }
        val decodedModel = DecodedModel.decode(input.modelState)
        val mode =
            if (input.tempC == null) {
                RecommendationMode.FALLBACK
            } else {
                activeMode(input.surveyedCount, decodedModel != null)
            }
        val pool = input.wardrobePieces.filter { !it.archived && it.laundryUntilEpochMs <= input.nowEpochMs }
        return RecommendationScoringContext(input, pool, typeById, mode, decodedModel)
    }

    private fun rankAndDeduplicate(scored: List<Pair<OutfitCandidate, Double>>): List<OutfitCandidate> =
        scored
            .sortedWith(CANDIDATE_RANK_ORDER)
            .distinctBy { (candidate, _) -> dedupKey(candidate) }
            .take(MAX_CANDIDATES)
            .map { it.first }

    private val CANDIDATE_RANK_ORDER =
        Comparator<Pair<OutfitCandidate, Double>> { left, right ->
            val scoreCmp = right.second.compareTo(left.second)
            if (scoreCmp != 0) return@Comparator scoreCmp
            val loggedCmp = (left.first.loggedEntry == null).compareTo(right.first.loggedEntry == null)
            if (loggedCmp != 0) return@Comparator loggedCmp
            val leftEntry = left.first.loggedEntry?.entry
            val rightEntry = right.first.loggedEntry?.entry
            val surveyCmp = (rightEntry?.surveyFeelingScore ?: Int.MIN_VALUE).compareTo(leftEntry?.surveyFeelingScore ?: Int.MIN_VALUE)
            if (surveyCmp != 0) return@Comparator surveyCmp
            val dateCmp = compareValues(rightEntry?.wornOnDate, leftEntry?.wornOnDate)
            if (dateCmp != 0) return@Comparator dateCmp
            (rightEntry?.createdAtEpochMs ?: Long.MIN_VALUE).compareTo(leftEntry?.createdAtEpochMs ?: Long.MIN_VALUE)
        }

    private fun dedupKey(candidate: OutfitCandidate): CandidateDedupKey =
        when {
            candidate.pieces.isNotEmpty() -> CandidateDedupKey.PieceSet(candidate.pieces.map { it.id }.sorted())
            candidate.loggedEntry != null -> CandidateDedupKey.SelfieOnly(candidate.loggedEntry.entry.id)
            else -> CandidateDedupKey.SuggestionOnly(candidate.generalSuggestion)
        }

    /**
     * Display groups for a candidate's pieces in fixed order; a full-body piece suppresses the
     * Upper/Lower groups; empty groups are omitted.
     */
    fun groupsFor(
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
    ): List<OutfitGroup> {
        val byZone = pieces.groupBy { piece -> groupZoneFor(typeById[piece.typeId]?.supertype ?: return@groupBy CoverageZone.ACCESSORIES) }
        val hasFullBody = CoverageZone.FULL_BODY in byZone
        return groupDisplayOrder
            .filter { zone -> byZone[zone]?.isNotEmpty() == true }
            .filter { zone -> !(hasFullBody && (zone == CoverageZone.UPPER || zone == CoverageZone.LOWER)) }
            .map { zone -> OutfitGroup(zone = zone, label = groupLabel(zone), pieces = byZone[zone].orEmpty()) }
    }

    /** Textual temperature band for fallback mode. */
    fun bandSuggestion(tempC: Double?): String =
        when {
            tempC == null -> "No forecast available — check the Weather tab"
            tempC < 5 -> "Winter jacket, sweater and long pants"
            tempC < 12 -> "Sweater and long pants"
            tempC < 18 -> "Long-sleeved shirt or light sweater"
            tempC < 24 -> "T-shirt or shirt"
            else -> "T-shirt, shorts if you like"
        }
}
