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

/**
 * Zone-based recommendation engine: builds candidates via [CandidateGenerator], scores them via
 * [Scoring] using a single pre-decoded model snapshot, and delegates slate selection to
 * [ExplorationPolicy].
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

    fun recommendOutfits(input: RecommendationInput): List<OutfitCandidate> = recommendSlate(input).candidates

    /** Returns both the top-3 [OutfitCandidate]s and their per-slot metadata. */
    fun recommendSlate(input: RecommendationInput): RankedSlate {
        val context = prepareContext(input)
        val candidates = CandidateGenerator.generate(context)
        val evaluated = candidates.map { candidate -> Scoring.evaluateCandidate(context, candidate) }
        return ExplorationPolicy.rankAndSelectSlate(context, evaluated)
    }

    /**
     * Suspending entry point for background ranking: yields every [SCORING_YIELD_INTERVAL]
     * candidates so single-threaded runtimes such as `wasmJs` never stall UI frames.
     */
    suspend fun recommendOutfitsSuspending(input: RecommendationInput): List<OutfitCandidate> = recommendSlateSuspending(input).candidates

    suspend fun recommendSlateSuspending(input: RecommendationInput): RankedSlate {
        val context = prepareContext(input)
        val candidates = CandidateGenerator.generate(context)
        val evaluated = ArrayList<EvaluatedCandidate>(candidates.size)
        candidates.forEachIndexed { index, candidate ->
            if (index > 0 && index % SCORING_YIELD_INTERVAL == 0) yield()
            evaluated += Scoring.evaluateCandidate(context, candidate)
        }
        return ExplorationPolicy.rankAndSelectSlate(context, evaluated)
    }

    private fun prepareContext(input: RecommendationInput): RecommendationScoringContext {
        val typeById = input.clothingTypes.associateBy { it.id }
        val decodedModel = DecodedModel.decode(input.modelState)
        val effectiveSurveyed =
            if (decodedModel != null) {
                minOf(input.surveyedCount, input.modelState?.sampleCount ?: input.surveyedCount)
            } else {
                input.surveyedCount
            }
        val mode =
            if (input.tempC == null) {
                RecommendationMode.FALLBACK
            } else {
                activeMode(effectiveSurveyed, decodedModel != null)
            }
        val pool = input.wardrobePieces.filter { !it.archived && it.laundryUntilEpochMs <= input.nowEpochMs }
        return RecommendationScoringContext(input, pool, typeById, mode, decodedModel)
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
