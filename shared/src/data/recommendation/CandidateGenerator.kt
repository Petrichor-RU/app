package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.recommendation.WarmthEncoding.warmth
import nl.petrichor.app.data.recommendation.WarmthEncoding.zoneWarmth
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.math.abs

/**
 * Candidate construction: logged outfits (with score-driven same-zone laundry replacements)
 * plus constructed per-zone combinations with multi-layer upper support.
 * Split outfits and full-body outfits remain mutually exclusive; outer layers are offered below
 * [OUTER_MAX_TEMP_C]. Logged and constructed candidates keep separate budgets before ranking.
 */
internal object CandidateGenerator {
    private const val OUTER_MAX_TEMP_C = 14.0
    private const val TOP_K_UPPER_BASE = 2
    private const val TOP_K_UPPER_OUTER = 2
    private const val TOP_K_LOWER = 3
    private const val TOP_K_FULL_BODY = 2
    private const val TOP_K_HEAD = 2
    private const val TOP_K_FEET = 1
    private const val TOP_K_ACCESSORIES = 1
    private const val BASE_WITH_OUTER_SHARE = 0.45

    private val OUTER_TYPE_NAMES = setOf("Jackets", "Hoodies", "Sweaters")

    // Per-zone share of the ideal total warmth, used to pick top-K options per zone.
    private val ZONE_SHARE =
        mapOf(
            CoverageZone.HEAD to 0.08,
            CoverageZone.UPPER to 0.45,
            CoverageZone.LOWER to 0.35,
            CoverageZone.FULL_BODY to 1.0,
            CoverageZone.FEET to 0.07,
            CoverageZone.ACCESSORIES to 0.0,
        )

    private data class LayerOptions(
        val upperBases: List<ClothingPiece>,
        val upperOuters: List<ClothingPiece>,
        val lowers: List<ClothingPiece>,
        val fullBodies: List<ClothingPiece>,
        val heads: List<ClothingPiece>,
        val feet: List<ClothingPiece>,
        val accessories: List<ClothingPiece>,
        val offerOuter: Boolean,
    )

    fun generate(context: RecommendationScoringContext): List<OutfitCandidate> {
        val source = RecommendationSource.valueOf(context.mode.name)
        val logged = loggedCandidates(context, source)
        val constructed =
            if (context.mode == RecommendationMode.FALLBACK) {
                fallbackConstructed(context.input, context.pool, context.typeById, source)
            } else {
                // Far outside the surveyed temperature range, constructed candidates carry the
                // textual band suggestion so the page leads with text (e.g. "T-shirt or shirt").
                val withBandText =
                    context.mode == RecommendationMode.HEURISTIC && isOutsideSurveyedRange(context.input)
                constructedCandidates(context.input, context.pool, context.typeById, source).map { candidate ->
                    if (withBandText && candidate.generalSuggestion == null) {
                        candidate.copy(generalSuggestion = RecommendationEngine.bandSuggestion(context.input.tempC))
                    } else {
                        candidate
                    }
                }
            }
        return logged + constructed
    }

    private fun loggedCandidates(
        context: RecommendationScoringContext,
        source: RecommendationSource,
    ): List<OutfitCandidate> =
        context.input.entries
            .sortedWith(
                compareByDescending<OutfitEntryWithPieces> { it.entry.wornOnDate }
                    .thenByDescending { it.entry.createdAtEpochMs },
            ).asSequence()
            .mapNotNull { entryWithPieces -> loggedCandidateFor(context, entryWithPieces, source) }
            .take(RecommendationEngine.MAX_LOGGED_CANDIDATES)
            .toList()

    private fun loggedCandidateFor(
        context: RecommendationScoringContext,
        entryWithPieces: OutfitEntryWithPieces,
        source: RecommendationSource,
    ): OutfitCandidate? {
        val entry = entryWithPieces.entry
        return when {
            entry.laundryUntilEpochMs > context.input.nowEpochMs -> {
                null
            }

            entryWithPieces.pieces.isEmpty() -> {
                OutfitCandidate(entryWithPieces, emptyList(), entry.photoPath, null, source)
            }

            else -> {
                LaundryReplacement
                    .resolvePieces(context, entryWithPieces)
                    ?.let { OutfitCandidate(entryWithPieces, it, entry.photoPath, null, source) }
            }
        }
    }

    private fun constructedCandidates(
        input: RecommendationInput,
        pool: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        source: RecommendationSource,
    ): List<OutfitCandidate> {
        val tempC = input.tempC ?: return emptyList()
        val options = buildLayerOptions(pool, typeById, tempC, Scoring.idealWarmth(input, typeById))
        return combinationsOf(options)
            .take(RecommendationEngine.MAX_CONSTRUCTED_CANDIDATES)
            .map { pieces ->
                OutfitCandidate(loggedEntry = null, pieces = pieces, selfiePath = null, generalSuggestion = null, source = source)
            }
    }

    private fun buildLayerOptions(
        pool: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        tempC: Double,
        ideal: Double,
    ): LayerOptions {
        val byZone = pool.groupBy { groupZoneFor(typeById[it.typeId]?.supertype ?: ClothingSupertype.ACCESSORIES) }
        val offerOuter = tempC < OUTER_MAX_TEMP_C
        val upperPool = byZone[CoverageZone.UPPER].orEmpty()
        val (outerUpperPool, baseUpperPool) = upperPool.partition { typeById[it.typeId]?.name in OUTER_TYPE_NAMES }
        val upperTarget = ideal * (ZONE_SHARE[CoverageZone.UPPER] ?: 0.0)
        val baseTarget = if (offerOuter && outerUpperPool.isNotEmpty()) upperTarget * BASE_WITH_OUTER_SHARE else upperTarget

        val selectedBases = rankByTarget(baseUpperPool, typeById, baseTarget, TOP_K_UPPER_BASE)
        val outerTarget = if (selectedBases.isNotEmpty()) upperTarget * (1.0 - BASE_WITH_OUTER_SHARE) else upperTarget
        val selectedOuters =
            if (offerOuter || baseUpperPool.isEmpty()) {
                rankByTarget(outerUpperPool, typeById, outerTarget, TOP_K_UPPER_OUTER)
            } else {
                emptyList()
            }

        return LayerOptions(
            upperBases = selectedBases,
            upperOuters = selectedOuters,
            lowers =
                rankByTarget(
                    byZone[CoverageZone.LOWER].orEmpty(),
                    typeById,
                    ideal * (ZONE_SHARE[CoverageZone.LOWER] ?: 0.0),
                    TOP_K_LOWER,
                ),
            fullBodies =
                rankByTarget(
                    byZone[CoverageZone.FULL_BODY].orEmpty(),
                    typeById,
                    ideal * (ZONE_SHARE[CoverageZone.FULL_BODY] ?: 0.0),
                    TOP_K_FULL_BODY,
                ),
            heads =
                rankByTarget(
                    byZone[CoverageZone.HEAD].orEmpty(),
                    typeById,
                    ideal * (ZONE_SHARE[CoverageZone.HEAD] ?: 0.0),
                    TOP_K_HEAD,
                ),
            feet =
                rankByTarget(
                    byZone[CoverageZone.FEET].orEmpty(),
                    typeById,
                    ideal * (ZONE_SHARE[CoverageZone.FEET] ?: 0.0),
                    TOP_K_FEET,
                ),
            accessories = rankByTarget(byZone[CoverageZone.ACCESSORIES].orEmpty(), typeById, 0.0, TOP_K_ACCESSORIES),
            offerOuter = offerOuter,
        )
    }

    private fun combinationsOf(options: LayerOptions): List<List<ClothingPiece>> {
        val bodyOptions = buildSplitBodyOptions(options) + buildFullBodyOptions(options)
        if (bodyOptions.isEmpty()) return emptyList()
        val headOptions = listOf(emptyList<ClothingPiece>()) + options.heads.map { listOf(it) }
        val feetOptions = listOf(emptyList<ClothingPiece>()) + options.feet.map { listOf(it) }
        val accessoryOptions = listOf(emptyList<ClothingPiece>()) + options.accessories.map { listOf(it) }
        return headOptions.flatMap { head ->
            bodyOptions.flatMap { body ->
                feetOptions.flatMap { feet ->
                    accessoryOptions.map { acc -> head + body + feet + acc }
                }
            }
        }
    }

    private fun buildSplitBodyOptions(options: LayerOptions): List<List<ClothingPiece>> {
        if (options.lowers.isEmpty()) return emptyList()
        val upperStacks =
            if (options.upperBases.isNotEmpty()) {
                val baseOnly = options.upperBases.map { listOf(it) }
                val layered =
                    if (options.offerOuter) {
                        options.upperBases.flatMap { base -> options.upperOuters.map { outer -> listOf(base, outer) } }
                    } else {
                        emptyList()
                    }
                baseOnly + layered
            } else {
                options.upperOuters.map { listOf(it) }
            }
        return upperStacks.flatMap { upper ->
            options.lowers.map { lower -> upper + lower }
        }
    }

    private fun buildFullBodyOptions(options: LayerOptions): List<List<ClothingPiece>> {
        val single = options.fullBodies.map { listOf(it) }
        val layered =
            if (options.offerOuter) {
                options.fullBodies.flatMap { full -> options.upperOuters.map { outer -> listOf(full, outer) } }
            } else {
                emptyList()
            }
        return single + layered
    }

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
}

private fun rankByTarget(
    pieces: List<ClothingPiece>,
    typeById: Map<Long, ClothingType>,
    target: Double,
    limit: Int,
): List<ClothingPiece> =
    pieces
        .sortedBy { piece -> abs(warmth(piece, typeById[piece.typeId]) - target) }
        .take(limit)

/** Total warmth of a piece set (upper + lower zones), used by the heuristic. */
internal fun outfitWarmth(
    pieces: List<ClothingPiece>,
    typeById: Map<Long, ClothingType>,
): Double = zoneWarmth(pieces, typeById, CoverageZone.UPPER) + zoneWarmth(pieces, typeById, CoverageZone.LOWER)
