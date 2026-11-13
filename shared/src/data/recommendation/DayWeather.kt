package nl.petrichor.app.data.recommendation

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.weather.WeatherSnapshot

internal data class DayWeather(
    val isRainy: Boolean = false,
    val minTemperature: Int? = null,
    val maxTemperature: Int? = null,
)

/** Mean temperature of a forecast day ((min + max) / 2, plan section 6.2); null without data. */
internal val DayWeather.tempC: Double?
    get() =
        when {
            minTemperature != null && maxTemperature != null -> (minTemperature + maxTemperature) / 2.0
            minTemperature != null -> minTemperature.toDouble()
            maxTemperature != null -> maxTemperature.toDouble()
            else -> null
        }

internal fun WeatherSnapshot.forecastDates(today: LocalDate): Set<LocalDate> =
    forecast.mapNotNull { runCatching { LocalDate.parse(it.label) }.getOrNull() }.toSet() + today

@Suppress("ReturnCount") // early returns keep the date branches readable
internal fun WeatherSnapshot?.dayWeatherFor(
    date: LocalDate,
    today: LocalDate,
): DayWeather {
    if (this == null) return DayWeather()
    val forecastDay = forecast.firstOrNull { it.label == date.toString() }
    if (date != today && forecastDay == null) return DayWeather()
    val isRainy =
        (date == today && description.contains("rain", ignoreCase = true)) ||
            forecastDay?.icon?.take(2) in setOf("09", "10", "11") // Checks the OpenWeatherMap icon code for rain
    return DayWeather(
        isRainy = isRainy,
        minTemperature = if (date == today) minOf(forecastDay?.min ?: temperature, temperature) else forecastDay?.min,
        maxTemperature = if (date == today) maxOf(forecastDay?.max ?: temperature, temperature) else forecastDay?.max,
    )
}
