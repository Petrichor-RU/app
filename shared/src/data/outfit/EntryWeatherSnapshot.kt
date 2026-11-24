package nl.petrichor.app.data.outfit

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.recommendation.dayWeatherFor
import nl.petrichor.app.data.weather.WeatherSnapshot

/**
 * Weather fields snapshotted onto an [OutfitEntry] at logging time, so the
 * model can train on the conditions the outfit was actually worn in.
 */
data class EntryWeatherSnapshot(
    val temperatureC: Double?,
    val feelsLikeC: Double?,
    val minTempC: Double?,
    val maxTempC: Double?,
    val weatherDescription: String?,
    val weatherIcon: String?,
    val isRainy: Boolean,
    val windSpeedMs: Double? = null,
    val humidity: Int? = null,
    val precipitationProbability: Double? = null,
    val rainVolumeMm: Double? = null,
    val weatherCapturedAtEpochMs: Long? = null,
    val locationName: String? = null,
)

/**
 * Computes the snapshot for an outfit logged on [date] given the current [weather] of the
 * shared selected place, delegating to the same [dayWeatherFor] summary helper used at
 * recommendation inference time.
 */
fun buildEntryWeatherSnapshot(
    weather: WeatherSnapshot?,
    date: LocalDate,
    today: LocalDate,
): EntryWeatherSnapshot {
    val summary = weather.dayWeatherFor(date, today)
    return EntryWeatherSnapshot(
        temperatureC = summary.currentTemperatureC,
        feelsLikeC = if (date == today) summary.feelsLikeC else null,
        minTempC = summary.minTemperature?.toDouble(),
        maxTempC = summary.maxTemperature?.toDouble(),
        weatherDescription = summary.weatherDescription,
        weatherIcon = summary.weatherIcon,
        isRainy = summary.isRainy,
        windSpeedMs = summary.windSpeedMs,
        humidity = summary.humidity,
        precipitationProbability = summary.precipitationProbability,
        rainVolumeMm = summary.rainVolumeMm,
        weatherCapturedAtEpochMs = summary.capturedAtEpochMs,
        locationName = summary.locationName,
    )
}
