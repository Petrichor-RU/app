package nl.petrichor.app.data.recommendation

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.isRainyWeather
import nl.petrichor.app.data.weather.ForecastDay
import nl.petrichor.app.data.weather.WeatherSnapshot

private const val RAIN_POP_THRESHOLD = 0.5
private const val RAIN_MM_THRESHOLD = 0.2

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

/** Mean temperature of a forecast day ((min + max) / 2, plan section 6.2); null without data. */
internal val DayWeather.tempC: Double?
    get() =
        when {
            minTemperature != null && maxTemperature != null -> (minTemperature + maxTemperature) / 2.0
            minTemperature != null -> minTemperature.toDouble()
            maxTemperature != null -> maxTemperature.toDouble()
            else -> currentTemperatureC
        }

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
    val rainy = isRainyWeather(snapshot.description, snapshot.icon) || isForecastRainy(forecastDay, pop, rainMm)
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
    isRainyWeather(null, forecastDay?.icon) ||
        (pop != null && pop >= RAIN_POP_THRESHOLD) ||
        (rainMm != null && rainMm >= RAIN_MM_THRESHOLD)
