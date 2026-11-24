package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.WarmthVerdict
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.math.abs

/** Target temperatures within this margin of a surveyed entry are still trusted. */
const val TEMP_MARGIN_C = 5.0

/** Penalty per degree beyond the margin for logged candidates worn at a different temperature. */
private const val PENALTY_PER_DEGREE_C = 0.25

/**
 * Built-in prior for the ideal warmth when the target temperature is far outside the surveyed
 * range: warmer days call for less warmth. Used instead of flat extrapolation, which would keep
 * a single PERFECT verdict's warmth ideal at every temperature.
 */
fun priorIdealWarmth(tempC: Double): Double = (3.6 - 0.12 * tempC).coerceIn(1.0, 5.0)

/** Training points (temperature, warmth, verdict) from surveyed entries with weather data. */
internal fun trainingPoints(
    input: RecommendationInput,
    typeById: Map<Long, ClothingType>,
): List<Triple<Double, Double, WarmthVerdict>> =
    input.entries.mapNotNull { entryWithPieces ->
        val entry = entryWithPieces.entry
        val temp = entryTemperature(entry)
        val verdict = entry.surveyWarmthVerdict
        if (temp != null && verdict != null) {
            Triple(temp, outfitWarmth(entryWithPieces.pieces, typeById), verdict)
        } else {
            null
        }
    }

/** Entry temperature: current reading, or (min+max)/2 when only a forecast snapshot exists. */
fun entryTemperature(entry: OutfitEntry): Double? =
    entry.temperatureC
        ?: if (entry.minTempC != null && entry.maxTempC != null) {
            (entry.minTempC + entry.maxTempC) / 2
        } else {
            null
        }

/**
 * True when the target temperature is further than [TEMP_MARGIN_C] outside the range of
 * surveyed entries with weather data — there the learned ideal is unreliable.
 */
fun isOutsideSurveyedRange(input: RecommendationInput): Boolean {
    val tempC = input.tempC
    val temps = trainingPoints(input, input.clothingTypes.associateBy { it.id }).map { it.first }
    return tempC != null &&
        temps.isNotEmpty() &&
        (tempC < temps.minOrNull()!! - TEMP_MARGIN_C || tempC > temps.maxOrNull()!! + TEMP_MARGIN_C)
}

/**
 * Penalty for a logged candidate whose entry was worn at a temperature far from the target
 * day; grows linearly beyond [TEMP_MARGIN_C]. Zero for constructed candidates.
 */
fun temperaturePenalty(
    candidate: OutfitCandidate,
    targetTempC: Double?,
): Double {
    val entryTemp = candidate.loggedEntry?.entry?.let { entryTemperature(it) }
    val target = targetTempC
    val distance = if (entryTemp != null && target != null) abs(entryTemp - target) else 0.0
    return if (distance > TEMP_MARGIN_C) PENALTY_PER_DEGREE_C * (distance - TEMP_MARGIN_C) else 0.0
}
