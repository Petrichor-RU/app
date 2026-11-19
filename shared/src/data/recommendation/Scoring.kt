package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.math.abs
import kotlin.math.min

/**
 * Candidate scoring:
 * - MODEL predicts the 1–5 feeling score with the decoded trained regression model.
 * - HEURISTIC compares actual vs ideal warmth.
 * - FALLBACK ranks logged outfits above the single constructed suggestion.
 * Selfie-only candidates (empty piece list) compete in MODEL/HEURISTIC using their own past
 * survey feeling score plus the temperature-distance penalty rather than garment warmth.
 */
internal object Scoring {
    private const val RAINY_BONUS = 0.3
    private const val DEFAULT_IDEAL_WARMTH = 3.0
    private const val DEFAULT_SELFIE_HISTORY_SCORE = 3.0

    fun scoreCandidate(
        input: RecommendationInput,
        candidate: OutfitCandidate,
        typeById: Map<Long, ClothingType>,
        mode: RecommendationMode,
        decodedModel: DecodedModel?,
    ): Double {
        val hasUsableModel = decodedModel != null && input.tempC != null && input.surveyedCount >= 1
        val baseScore =
            when {
                mode == RecommendationMode.FALLBACK -> fallbackRank(candidate)
                candidate.pieces.isEmpty() && candidate.loggedEntry != null -> selfieHistoryScore(candidate)
                hasUsableModel -> modelScore(input, candidate.pieces, typeById, decodedModel)
                else -> heuristicScore(input, candidate, typeById)
            }
        return baseScore - temperaturePenalty(candidate, input.tempC)
    }

    /** MODEL: predicted 1–5 feeling score using the decoded Bayesian rating posterior. */
    fun modelScore(
        input: RecommendationInput,
        pieces: List<ClothingPiece>,
        typeById: Map<Long, ClothingType>,
        decodedModel: DecodedModel?,
    ): Double {
        val tempC = input.tempC
        val model = decodedModel ?: DecodedModel.decode(input.modelState)
        return if (tempC == null || model == null) {
            0.0
        } else {
            BayesianRatingModel.predict(model, tempC, input.isRainy, pieces, typeById).mean
        }
    }

    /** Convenience overload kept for unit tests and legacy callers. */
    fun modelScore(
        input: RecommendationInput,
        candidate: OutfitCandidate,
        typeById: Map<Long, ClothingType>,
    ): Double = modelScore(input, candidate.pieces, typeById, DecodedModel.decode(input.modelState))

    /** HEURISTIC: 5 minus the warmth distance to the ideal, capped at a 4-point penalty. */
    fun heuristicScore(
        input: RecommendationInput,
        candidate: OutfitCandidate,
        typeById: Map<Long, ClothingType>,
    ): Double {
        val ideal = idealWarmth(input, typeById)
        val actual = outfitWarmth(candidate.pieces, typeById)
        return 5.0 - min(4.0, abs(actual - ideal))
    }

    /** FALLBACK: logged outfits rank above the constructed one; both keep a stable order. */
    fun fallbackRank(candidate: OutfitCandidate): Double =
        when {
            candidate.loggedEntry != null -> 2.0
            candidate.generalSuggestion != null -> 1.0
            else -> 0.0
        }

    private fun selfieHistoryScore(candidate: OutfitCandidate): Double =
        candidate.loggedEntry
            ?.entry
            ?.surveyFeelingScore
            ?.toDouble()
            ?.coerceIn(1.0, 5.0)
            ?: DEFAULT_SELFIE_HISTORY_SCORE

    /**
     * Ideal total warmth (upper + lower) at the forecast temperature, learned from the survey
     * verdicts: interpolate PERFECT entries when there are at least two, otherwise take the
     * midpoint between the nearest TOO_COLD and TOO_WARM entries; +0.3 when rainy.
     */
    fun idealWarmth(
        input: RecommendationInput,
        typeById: Map<Long, ClothingType>,
    ): Double {
        val tempC = input.tempC ?: return DEFAULT_IDEAL_WARMTH
        val training = trainingPoints(input, typeById)
        // Far outside the surveyed temperature range the verdicts don't transfer; use the prior.
        val base =
            if (isOutsideSurveyedRange(input)) {
                priorIdealWarmth(tempC)
            } else {
                idealFromVerdicts(training, tempC)
            }
        return base + if (input.isRainy) RAINY_BONUS else 0.0
    }

    private fun idealFromVerdicts(
        training: List<Triple<Double, Double, WarmthVerdict>>,
        tempC: Double,
    ): Double {
        val perfect = training.filter { it.third == WarmthVerdict.PERFECT }.sortedBy { it.first }
        return when {
            perfect.size >= 2 -> interpolate(perfect.map { it.first to it.second }, tempC)
            perfect.size == 1 -> perfect.first().second
            else -> coldWarmMidpoint(training, tempC)
        }
    }

    /** Midpoint between the nearest TOO_COLD and TOO_WARM entries at this temperature. */
    private fun coldWarmMidpoint(
        training: List<Triple<Double, Double, WarmthVerdict>>,
        tempC: Double,
    ): Double {
        val cold = training.filter { it.third == WarmthVerdict.TOO_COLD }.minByOrNull { abs(it.first - tempC) }
        val warm = training.filter { it.third == WarmthVerdict.TOO_WARM }.minByOrNull { abs(it.first - tempC) }
        return when {
            cold != null && warm != null -> (cold.second + warm.second) / 2
            cold != null -> cold.second
            warm != null -> warm.second
            else -> DEFAULT_IDEAL_WARMTH
        }
    }

    /** Piecewise-linear interpolation over sorted (temperature, warmth) points, clamped at the ends. */
    private fun interpolate(
        points: List<Pair<Double, Double>>,
        tempC: Double,
    ): Double {
        val sorted = points.sortedBy { it.first }
        if (sorted.isEmpty()) return DEFAULT_IDEAL_WARMTH
        // Clamp at the ends: tempC can sit inside the overall surveyed range but outside the
        // range of the PERFECT verdicts this interpolation runs over.
        val lower = sorted.lastOrNull { it.first <= tempC } ?: sorted.first()
        val upper = sorted.firstOrNull { it.first >= tempC } ?: sorted.last()
        return if (upper.first == lower.first) {
            upper.second
        } else {
            val t = (tempC - lower.first) / (upper.first - lower.first)
            lower.second + t * (upper.second - lower.second)
        }
    }
}
