package nl.petrichor.app.data.recommendation

import kotlinx.serialization.json.Json
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.math.abs
import kotlin.math.min

/**
 * Candidate scoring (plan section 6.4): MODEL predicts the 1-5 feeling score with the trained
 * linear regression, HEURISTIC compares actual vs ideal warmth, FALLBACK ranks logged outfits
 * above the single constructed one.
 */
internal object Scoring {
    private const val RAINY_BONUS = 0.3
    private const val DEFAULT_IDEAL_WARMTH = 3.0

    /** MODEL: predicted feeling score clamped to 1..5. */
    fun modelScore(
        input: RecommendationInput,
        candidate: OutfitCandidate,
        typeById: Map<Long, ClothingType>,
    ): Double {
        val state = input.modelState
        val tempC = input.tempC
        if (state == null || tempC == null) return 0.0
        val coefficients = Json.decodeFromString<List<Double>>(state.coefficientsJson).toDoubleArray()
        val features = WarmthEncoding.featureVector(tempC, input.isRainy, candidate.pieces, typeById)
        return LinearRegression.predict(coefficients, features).coerceIn(1.0, 5.0)
    }

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
