package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.recommendation.WarmthEncoding.warmth
import nl.petrichor.app.data.recommendation.WarmthEncoding.zonesFor
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.math.abs

private const val LAUNDRY_REPLACEMENT_TOP_K = 5

internal object LaundryReplacement {
    /**
     * Resolves a logged entry's pieces against the clean pool; returns null when the outfit
     * cannot be worn (an archived piece or an essential zone in laundry with no clean replacement).
     * Dirty pieces evaluate the top-K clean same-zone replacements using the active scorer and
     * pick the highest-scoring replacement.
     */
    fun resolvePieces(
        context: RecommendationScoringContext,
        entryWithPieces: OutfitEntryWithPieces,
    ): List<ClothingPiece>? {
        val originalPieces = entryWithPieces.pieces
        val cleanUntouched =
            originalPieces.filter { !it.archived && it.laundryUntilEpochMs <= context.input.nowEpochMs }
        val resolved = mutableListOf<ClothingPiece>()
        var skip = false

        for (piece in originalPieces) {
            when {
                piece.archived -> {
                    skip = true
                }

                piece.laundryUntilEpochMs > context.input.nowEpochMs -> {
                    val remainingClean = cleanUntouched.filter { candidate -> resolved.none { it.id == candidate.id } }
                    val replacement =
                        bestScoredReplacement(
                            piece = piece,
                            alreadyResolved = resolved,
                            remainingClean = remainingClean,
                            context = context,
                            entryWithPieces = entryWithPieces,
                        )
                    when {
                        replacement == null && isEssentialZone(piece, context.typeById) -> skip = true
                        replacement != null -> resolved += replacement
                    }
                }

                else -> {
                    if (resolved.none { it.id == piece.id }) {
                        resolved += piece
                    }
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

    private fun bestScoredReplacement(
        piece: ClothingPiece,
        alreadyResolved: List<ClothingPiece>,
        remainingClean: List<ClothingPiece>,
        context: RecommendationScoringContext,
        entryWithPieces: OutfitEntryWithPieces,
    ): ClothingPiece? {
        val zones = zonesFor(context.typeById[piece.typeId]?.supertype ?: ClothingSupertype.ACCESSORIES)
        val originalWarmth = warmth(piece, context.typeById[piece.typeId])
        val usedIds = alreadyResolved.mapTo(mutableSetOf()) { it.id }
        val topK =
            if (zones.isEmpty()) {
                emptyList()
            } else {
                context.pool
                    .filter { candidate ->
                        candidate.id !in usedIds &&
                            zonesFor(context.typeById[candidate.typeId]?.supertype ?: ClothingSupertype.ACCESSORIES) == zones
                    }.sortedBy { candidate -> abs(warmth(candidate, context.typeById[candidate.typeId]) - originalWarmth) }
                    .take(LAUNDRY_REPLACEMENT_TOP_K)
            }
        val source = RecommendationSource.valueOf(context.mode.name)
        return topK.maxByOrNull { replacement ->
            val trialPieces = (alreadyResolved + replacement + remainingClean).distinctBy { it.id }
            val trialCandidate =
                OutfitCandidate(
                    loggedEntry = entryWithPieces,
                    pieces = trialPieces,
                    selfiePath = entryWithPieces.entry.photoPath,
                    generalSuggestion = null,
                    source = source,
                )
            Scoring.scoreCandidate(context.input, trialCandidate, context.typeById, context.mode, context.decodedModel)
        }
    }
}
