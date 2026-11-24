package nl.petrichor.app.data.outfit

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.weather.WeatherConditions
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
)

/**
 * Computes the snapshot for an outfit logged on [date] given the current [weather] of the
 * shared selected place:
 * - [date] == today: current temperature/feels-like, today's forecast min/max, description, icon and isRainy.
 * - [date] is a future day covered by the forecast: that day's min/max and icon, current-temp fields null.
 * - otherwise (older manual back-entries): all null, isRainy = false; the entry is excluded from training.
 */
fun buildEntryWeatherSnapshot(
    weather: WeatherSnapshot?,
    date: LocalDate,
    today: LocalDate,
): EntryWeatherSnapshot {
    if (weather == null) return EntryWeatherSnapshot(null, null, null, null, null, null, isRainy = false)
    val forecastDay = weather.forecast.firstOrNull { it.label == date.toString() }
    return when {
        date == today -> {
            EntryWeatherSnapshot(
                temperatureC = weather.temperature.toDouble(),
                feelsLikeC = weather.feelsLike.toDouble(),
                minTempC = minOf(forecastDay?.min ?: weather.temperature, weather.temperature).toDouble(),
                maxTempC = maxOf(forecastDay?.max ?: weather.temperature, weather.temperature).toDouble(),
                weatherDescription = weather.description,
                weatherIcon = weather.icon,
                isRainy =
                    WeatherConditions.isRainy(weather.description, weather.conditionId, weather.icon) ||
                        WeatherConditions.isRainy(null, forecastDay?.conditionId, forecastDay?.icon),
            )
        }

        forecastDay != null -> {
            EntryWeatherSnapshot(
                temperatureC = null,
                feelsLikeC = null,
                minTempC = forecastDay.min.toDouble(),
                maxTempC = forecastDay.max.toDouble(),
                weatherDescription = null,
                weatherIcon = forecastDay.icon,
                isRainy = WeatherConditions.isRainy(null, forecastDay.conditionId, forecastDay.icon),
            )
        }

        else -> {
            EntryWeatherSnapshot(null, null, null, null, null, null, isRainy = false)
        }
    }
}
