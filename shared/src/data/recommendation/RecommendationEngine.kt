package nl.petrichor.app.data.recommendation

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.ModelState
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.random.Random
import kotlin.time.Clock

/** Scoring mode derived from the amount of survey data (plan section 6.4). */
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

/** A labeled display group of pieces (plan section 6.5). */
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

/**
 * Zone-based recommendation engine (plan section 6): builds candidates from logged outfits
 * (with laundry replacements) and constructed combinations, scores them with the active mode
 * and returns the top few.
 */
object RecommendationEngine {
    const val MAX_CANDIDATES = 3
    const val MAX_CONSTRUCTED_CANDIDATES = 60
    const val MODEL_MIN_SURVEYED = 10

    /** Model mode needs >= 10 surveyed entries and a trained model; 1-9 surveyed is heuristic; none is fallback. */
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
        val typeById = input.clothingTypes.associateBy { it.id }
        val mode =
            if (input.tempC == null) {
                RecommendationMode.FALLBACK
            } else {
                activeMode(input.surveyedCount, input.modelState != null)
            }
        val pool = input.wardrobePieces.filter { !it.archived && it.laundryUntilEpochMs <= input.nowEpochMs }
        val candidates = CandidateGenerator.generate(input, pool, typeById, mode)
        val scored =
            when (mode) {
                RecommendationMode.MODEL -> {
                    candidates.map {
                        it to
                            Scoring.modelScore(input, it, typeById) - temperaturePenalty(it, input.tempC)
                    }
                }

                RecommendationMode.HEURISTIC -> {
                    candidates.map {
                        it to
                            Scoring.heuristicScore(input, it, typeById) - temperaturePenalty(it, input.tempC)
                    }
                }

                RecommendationMode.FALLBACK -> {
                    candidates.map { it to Scoring.fallbackRank(it) - temperaturePenalty(it, input.tempC) }
                }
            }
        return scored
            .distinctBy { (candidate, _) -> candidate.pieces.map { it.id }.sorted() to candidate.loggedEntry?.entry?.id }
            .sortedWith(compareByDescending<Pair<OutfitCandidate, Double>> { it.second }.thenBy { it.first.loggedEntry == null })
            .take(MAX_CANDIDATES)
            .map { it.first }
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

    /** Textual temperature band for fallback mode (plan section 6.4). */
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
