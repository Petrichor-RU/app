package nl.petrichor.app.data.recommendation

import nl.petrichor.app.data.outfit.OutfitEntry

internal const val WIND_EXPOSURE_SCALE_MS = 10.0
internal const val MAX_WIND_EXPOSURE = 1.5
internal const val RAINY_FLAG_BASE_EXPOSURE = 0.6
internal const val RAIN_VOLUME_SCALE_MM = 5.0
private const val GOT_WET_MIN_EXPOSURE = 0.85
private const val INDOOR_OBSERVATION_WEIGHT = 0.35
private const val HIGH_ACTIVITY_OBSERVATION_WEIGHT = 0.50

/**
 * Normalised wind exposure shared identically between training snapshots ([OutfitEntry]) and
 * inference ([DayWeather] / [RecommendationInput]). Uses air temperature + explicit wind
 * exposure rather than `feelsLikeC` to avoid double-counting wind chill.
 */
internal fun computeWindExposure(windSpeedMs: Double?): Double =
    if (windSpeedMs == null || !windSpeedMs.isFinite()) {
        0.0
    } else {
        (windSpeedMs / WIND_EXPOSURE_SCALE_MS).coerceIn(0.0, MAX_WIND_EXPOSURE)
    }

/**
 * Normalised rain exposure shared identically between training snapshots and inference,
 * combining the weather icon/flag, precipitation probability, and 3-hour rain volume.
 */
internal fun computeRainExposure(
    isRainy: Boolean,
    precipitationProbability: Double? = null,
    rainVolumeMm: Double? = null,
): Double {
    val flagPart = if (isRainy) RAINY_FLAG_BASE_EXPOSURE else 0.0
    val popPart =
        if (precipitationProbability != null && precipitationProbability.isFinite()) {
            precipitationProbability.coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    val volPart =
        if (rainVolumeMm != null && rainVolumeMm.isFinite()) {
            (rainVolumeMm / RAIN_VOLUME_SCALE_MM).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    return maxOf(flagPart, popPart, volPart)
}

/**
 * Resolves effective wet exposure during training: when the wearer explicitly answered `gotWet`,
 * dry trips under forecast rain decouple to `0.0` while confirmed wet trips reach at least
 * `0.85` even if forecast precipitation looked modest.
 */
internal fun effectiveTrainingRainExposure(entry: OutfitEntry): Double =
    when (entry.gotWet) {
        true -> maxOf(entry.rainExposure, GOT_WET_MIN_EXPOSURE)
        false -> 0.0
        null -> entry.rainExposure
    }

/**
 * Down-weights indoor or unusually active observations during training by scaling observation
 * variance (`R_eff = R / weight`), preventing atypical days from skewing outdoor comfort estimates.
 */
internal fun contextObservationWeight(entry: OutfitEntry): Double {
    var weight = 1.0
    if (entry.mostlyIndoors == true) weight *= INDOOR_OBSERVATION_WEIGHT
    if (entry.highActivity == true) weight *= HIGH_ACTIVITY_OBSERVATION_WEIGHT
    return weight
}

internal val DayWeather.windExposure: Double
    get() = computeWindExposure(windSpeedMs)

internal val DayWeather.rainExposure: Double
    get() = computeRainExposure(isRainy, precipitationProbability, rainVolumeMm)

internal val OutfitEntry.windExposure: Double
    get() = computeWindExposure(windSpeedMs)

internal val OutfitEntry.rainExposure: Double
    get() = computeRainExposure(isRainy, precipitationProbability, rainVolumeMm)

internal val RecommendationInput.windExposure: Double
    get() = computeWindExposure(windSpeedMs)

internal val RecommendationInput.rainExposure: Double
    get() = computeRainExposure(isRainy, precipitationProbability, rainVolumeMm)
