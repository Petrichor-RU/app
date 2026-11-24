package nl.petrichor.app.data.recommendation

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.weather.ForecastDay
import nl.petrichor.app.data.weather.WeatherConditions
import nl.petrichor.app.data.weather.WeatherSnapshot

private const val RAIN_POP_THRESHOLD = 0.5
private const val RAIN_MM_THRESHOLD = 0.2
internal const val WIND_EXPOSURE_SCALE_MS = 10.0
internal const val MAX_WIND_EXPOSURE = 1.5
internal const val RAINY_FLAG_BASE_EXPOSURE = 0.6
internal const val RAIN_VOLUME_SCALE_MM = 5.0
private const val GOT_WET_MIN_EXPOSURE = 0.85
private const val INDOOR_OBSERVATION_WEIGHT = 0.35
private const val HIGH_ACTIVITY_OBSERVATION_WEIGHT = 0.50

internal data class DayWeather(
    val isRainy: Boolean = false,
    val minTemperature: Int? = null,
    val maxTemperature: Int? = null,
    val feelsLikeC: Double? = null,
    val windSpeedMs: Double? = null,
    val humidity: Int? = null,
    val precipitationProbability: Double? = null,
    val rainVolumeMm: Double? = null,
    val currentTemperatureC: Double? = null,
    val weatherDescription: String? = null,
    val weatherIcon: String? = null,
    val capturedAtEpochMs: Long? = null,
    val locationName: String? = null,
)

/** Mean temperature of a forecast day ((min + max) / 2); null without data. */
internal val DayWeather.tempC: Double?
    get() =
        when {
            minTemperature != null && maxTemperature != null -> (minTemperature + maxTemperature) / 2.0
            minTemperature != null -> minTemperature.toDouble()
            maxTemperature != null -> maxTemperature.toDouble()
            else -> currentTemperatureC
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

internal fun WeatherSnapshot.forecastDates(today: LocalDate): Set<LocalDate> =
    forecast.mapNotNull { runCatching { LocalDate.parse(it.label) }.getOrNull() }.toSet() + today

/**
 * Shared weather summary helper used by both training snapshots ([buildEntryWeatherSnapshot])
 * and inference ([RecommendationEngine]).
 */
@Suppress("ReturnCount") // early returns keep the date branches readable
internal fun WeatherSnapshot?.dayWeatherFor(
    date: LocalDate,
    today: LocalDate,
): DayWeather {
    if (this == null) return DayWeather()
    val forecastDay = forecast.firstOrNull { it.label == date.toString() }
    if (date != today && forecastDay == null) return DayWeather()
    return if (date == today) {
        todayDayWeather(this, forecastDay)
    } else {
        forecastDayWeather(this, forecastDay)
    }
}

private fun todayDayWeather(
    snapshot: WeatherSnapshot,
    forecastDay: ForecastDay?,
): DayWeather {
    val pop = forecastDay?.precipitationProbability
    val rainMm = snapshot.rainVolumeMm ?: forecastDay?.rainVolumeMm
    val rainy =
        WeatherConditions.isRainy(snapshot.description, snapshot.conditionId, snapshot.icon) ||
            isForecastRainy(forecastDay, pop, rainMm)
    return DayWeather(
        isRainy = rainy,
        minTemperature = minOf(forecastDay?.min ?: snapshot.temperature, snapshot.temperature),
        maxTemperature = maxOf(forecastDay?.max ?: snapshot.temperature, snapshot.temperature),
        feelsLikeC = snapshot.feelsLikeC ?: snapshot.feelsLike.toDouble(),
        windSpeedMs = snapshot.windSpeedMs ?: forecastDay?.windSpeedMs ?: snapshot.windSpeed.toDouble(),
        humidity = snapshot.humidity,
        precipitationProbability = pop,
        rainVolumeMm = rainMm,
        currentTemperatureC = snapshot.temperatureC ?: snapshot.temperature.toDouble(),
        weatherDescription = snapshot.description,
        weatherIcon = snapshot.icon,
        capturedAtEpochMs = snapshot.capturedAtEpochMs,
        locationName = snapshot.place.name,
    )
}

private fun forecastDayWeather(
    snapshot: WeatherSnapshot,
    forecastDay: ForecastDay?,
): DayWeather {
    val pop = forecastDay?.precipitationProbability
    val rainMm = forecastDay?.rainVolumeMm
    return DayWeather(
        isRainy = isForecastRainy(forecastDay, pop, rainMm),
        minTemperature = forecastDay?.min,
        maxTemperature = forecastDay?.max,
        feelsLikeC = forecastDay?.feelsLikeC,
        windSpeedMs = forecastDay?.windSpeedMs,
        humidity = forecastDay?.humidity,
        precipitationProbability = pop,
        rainVolumeMm = rainMm,
        currentTemperatureC = null,
        weatherDescription = null,
        weatherIcon = forecastDay?.icon,
        capturedAtEpochMs = snapshot.capturedAtEpochMs,
        locationName = snapshot.place.name,
    )
}

private fun isForecastRainy(
    forecastDay: ForecastDay?,
    pop: Double?,
    rainMm: Double?,
): Boolean =
    WeatherConditions.isRainy(null, forecastDay?.conditionId, forecastDay?.icon) ||
        (pop != null && pop >= RAIN_POP_THRESHOLD) ||
        (rainMm != null && rainMm >= RAIN_MM_THRESHOLD)

/**
 * Normalised wind exposure shared identically between training snapshots ([OutfitEntry]) and
 * inference ([DayWeather] / [RecommendationInput]). Uses air temperature plus explicit wind
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
