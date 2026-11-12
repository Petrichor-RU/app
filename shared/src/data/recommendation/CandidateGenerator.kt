package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.recommendation.WarmthEncoding.warmth
import nl.petrichor.app.data.recommendation.WarmthEncoding.zoneWarmth
import nl.petrichor.app.data.recommendation.WarmthEncoding.zonesFor
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType

/**
 * Candidate construction (plan section 6.3): logged outfits (with same-zone laundry
 * replacements) plus constructed per-zone combinations. Split outfits and full-body outfits
 * are mutually exclusive; outer layers are only offered below 14 °C.
 */
internal object CandidateGenerator {
    // Top-K options per display zone.
    private val TOP_K =
        mapOf(
            CoverageZone.HEAD to 3,
            CoverageZone.UPPER to 2,
            CoverageZone.LOWER to 3,
            CoverageZone.FULL_BODY to 2,
            CoverageZone.FEET to 1,
            CoverageZone.ACCESSORIES to 1,
        )

    private val OUTER_TYPE_NAMES = setOf("Jackets", "Hoodies", "Sweaters")
    private const val OUTER_MAX_TEMP_C = 14.0

    // Per-zone share of the ideal total warmth, used to rank constructed options.
    private val ZONE_SHARE =
        mapOf(
            CoverageZone.HEAD to 0.08,
            CoverageZone.UPPER to 0.45,
            CoverageZone.LOWER to 0.35,
            CoverageZone.FULL_BODY to 1.0,
            CoverageZone.FEET to 0.07,
            CoverageZone.ACCESSORIES to 0.0,
        )

    fun generate(
        input: RecommendationInput,
        pool: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        mode: RecommendationMode,
    ): List<OutfitCandidate> {
        val source = RecommendationSource.valueOf(mode.name)
        val logged = loggedCandidates(input, pool, typeById, source)
        return when (mode) {
            RecommendationMode.FALLBACK -> {
                logged + fallbackConstructed(input, pool, typeById, source)
            }

            else -> {
                // Far outside the surveyed temperature range, constructed candidates carry the
                // textual band suggestion so the page leads with text (e.g. "T-shirt or shirt").
                val withBandText = mode == RecommendationMode.HEURISTIC && isOutsideSurveyedRange(input)
                val constructed =
                    constructedCandidates(input, pool, typeById, source).map { candidate ->
                        if (withBandText && candidate.generalSuggestion == null) {
                            candidate.copy(generalSuggestion = RecommendationEngine.bandSuggestion(input.tempC))
                        } else {
                            candidate
                        }
                    }
                (logged + constructed).take(RecommendationEngine.MAX_CONSTRUCTED_CANDIDATES)
            }
        }
    }

    private fun loggedCandidates(
        input: RecommendationInput,
        pool: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        source: RecommendationSource,
    ): List<OutfitCandidate> =
        input.entries.mapNotNull { entryWithPieces ->
            loggedCandidateFor(entryWithPieces, pool, typeById, source, input.nowEpochMs)
        }

    /**
     * One logged entry as a candidate, or null when it must be skipped: the entry is in the
     * laundry, a piece is archived, or a laundry piece has no same-zone alternative in an
     * essential zone (UPPER/LOWER). Selfie-only entries stay valid as selfie cards.
     */
    private fun loggedCandidateFor(
        entryWithPieces: OutfitEntryWithPieces,
        pool: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        source: RecommendationSource,
        nowEpochMs: Long,
    ): OutfitCandidate? {
        val entry = entryWithPieces.entry
        return when {
            entry.laundryUntilEpochMs > nowEpochMs -> {
                null
            }

            entryWithPieces.pieces.isEmpty() -> {
                OutfitCandidate(entryWithPieces, emptyList(), entry.photoPath, null, source)
            }

            else -> {
                resolveLoggedPieces(entryWithPieces, pool, typeById, nowEpochMs)?.let {
                    OutfitCandidate(entryWithPieces, it, entry.photoPath, null, source)
                }
            }
        }
    }

    /**
     * Resolves a logged entry's pieces against the clean pool; null means skip the candidate.
     * A laundry piece is replaced by the best same-zone alternative; without an alternative the
     * candidate is skipped for UPPER/LOWER, while HEAD/FEET pieces are simply dropped.
     */
    private fun resolveLoggedPieces(
        entryWithPieces: OutfitEntryWithPieces,
        pool: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        nowEpochMs: Long,
    ): List<ClothingPiece>? {
        val resolved = mutableListOf<ClothingPiece>()
        var skip = false
        for (piece in entryWithPieces.pieces) {
            when {
                piece.archived -> {
                    skip = true
                }

                piece.laundryUntilEpochMs > nowEpochMs -> {
                    val replacement = bestSameZoneReplacement(piece, pool, typeById)
                    when {
                        replacement == null && isEssentialZone(piece, typeById) -> skip = true
                        replacement != null -> resolved += replacement
                    }
                }

                else -> {
                    resolved += piece
                }
            }
            if (skip) break
        }
        return if (skip) null else resolved.distinctBy { it.id }
    }

    private fun isEssentialZone(
        piece: ClothingPiece,
        typeById: Map<Long, ClothingType>,
    ): Boolean {
        val zones = zonesFor(typeById[piece.typeId]?.supertype ?: ClothingSupertype.ACCESSORIES)
        return CoverageZone.UPPER in zones || CoverageZone.LOWER in zones
    }

    /** Best same-zone alternative: the clean pool piece whose warmth is closest to the original. */
    private fun bestSameZoneReplacement(
        piece: ClothingPiece,
        pool: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
    ): ClothingPiece? {
        val zones = zonesFor(typeById[piece.typeId]?.supertype ?: ClothingSupertype.ACCESSORIES)
        if (zones.isEmpty()) return null
        val originalWarmth = warmth(piece, typeById[piece.typeId])
        return pool
            .filter { zonesFor(typeById[it.typeId]?.supertype ?: ClothingSupertype.ACCESSORIES) == zones }
            .minByOrNull { kotlin.math.abs(warmth(it, typeById[it.typeId]) - originalWarmth) }
    }

    /** Constructed combinations: per-zone top-K options combined, split vs full-body exclusive, capped. */
    private fun constructedCandidates(
        input: RecommendationInput,
        pool: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        source: RecommendationSource,
    ): List<OutfitCandidate> {
        val tempC = input.tempC
        val options =
            if (tempC != null) {
                topKOptions(pool, typeById, tempC, Scoring.idealWarmth(input, typeById))
            } else {
                emptyMap()
            }
        return combinationsOf(options)
            .take(RecommendationEngine.MAX_CONSTRUCTED_CANDIDATES)
            .map { pieces ->
                OutfitCandidate(loggedEntry = null, pieces = pieces, selfiePath = null, generalSuggestion = null, source = source)
            }
    }

    /** head x (upper x lower | full-body) x feet x accessories; empty allowed for head/feet/accessories. */
    private fun combinationsOf(options: Map<CoverageZone, List<ClothingPiece>>): List<List<ClothingPiece>> {
        val upperLower =
            options[CoverageZone.UPPER].orEmpty().flatMap { upper ->
                options[CoverageZone.LOWER].orEmpty().map { lower -> listOf(upper, lower) }
            }
        val bodyOptions = upperLower + options[CoverageZone.FULL_BODY].orEmpty().map { listOf(it) }
        if (bodyOptions.isEmpty()) return emptyList()
        val headOptions = listOf(emptyList<ClothingPiece>()) + options[CoverageZone.HEAD].orEmpty().map { listOf(it) }
        val feetOptions = listOf(emptyList<ClothingPiece>()) + options[CoverageZone.FEET].orEmpty().map { listOf(it) }
        val accessoryOptions = listOf(emptyList<ClothingPiece>()) + options[CoverageZone.ACCESSORIES].orEmpty().map { listOf(it) }
        return headOptions.flatMap { head ->
            bodyOptions.flatMap { body ->
                feetOptions.flatMap { feet ->
                    accessoryOptions.map { acc -> head + body + feet + acc }
                }
            }
        }
    }

    /** Fallback mode (0 surveys): one constructed candidate with a random piece per zone plus the band text. */
    private fun fallbackConstructed(
        input: RecommendationInput,
        pool: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        source: RecommendationSource,
    ): List<OutfitCandidate> {
        val pieces =
            if (pool.isEmpty()) {
                emptyList()
            } else {
                val byZone = pool.groupBy { groupZoneFor(typeById[it.typeId]?.supertype ?: ClothingSupertype.ACCESSORIES) }
                val hasFullBody = byZone[CoverageZone.FULL_BODY]?.isNotEmpty() == true
                groupDisplayOrder
                    .filter { zone -> byZone[zone]?.isNotEmpty() == true }
                    .filter { zone -> !(hasFullBody && (zone == CoverageZone.UPPER || zone == CoverageZone.LOWER)) }
                    .mapNotNull { zone -> byZone[zone]?.randomOrNull(input.random) }
            }
        return listOf(
            OutfitCandidate(
                loggedEntry = null,
                pieces = pieces,
                selfiePath = null,
                generalSuggestion = RecommendationEngine.bandSuggestion(input.tempC),
                source = source,
            ),
        )
    }

    /** Per-zone top-K pool pieces ranked by warmth closeness to the zone's share of the ideal warmth. */
    private fun topKOptions(
        pool: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        tempC: Double,
        ideal: Double,
    ): Map<CoverageZone, List<ClothingPiece>> {
        val byZone = pool.groupBy { groupZoneFor(typeById[it.typeId]?.supertype ?: ClothingSupertype.ACCESSORIES) }
        return groupDisplayOrder.associateWith { zone ->
            val target = ideal * (ZONE_SHARE[zone] ?: 0.0)
            byZone[zone]
                .orEmpty()
                // Outer layers (jackets/hoodies/sweaters) are only offered below OUTER_MAX_TEMP_C.
                .filter { piece ->
                    tempC < OUTER_MAX_TEMP_C || zone !in setOf(CoverageZone.UPPER, CoverageZone.FULL_BODY) ||
                        typeById[piece.typeId]?.name !in OUTER_TYPE_NAMES
                }.sortedBy { piece -> kotlin.math.abs(warmth(piece, typeById[piece.typeId]) - target) }
                .take(TOP_K[zone] ?: 1)
        }
    }
}

/** Total warmth of a piece set (upper + lower zones), used by the heuristic. */
internal fun outfitWarmth(
    pieces: List<ClothingPiece>,
    typeById: Map<Long, ClothingType>,
): Double = zoneWarmth(pieces, typeById, CoverageZone.UPPER) + zoneWarmth(pieces, typeById, CoverageZone.LOWER)
