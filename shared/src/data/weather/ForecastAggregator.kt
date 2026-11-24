package nl.petrichor.app.data.weather

import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

private const val DAYTIME_START_HOUR = 6
private const val DAYTIME_END_HOUR = 22
private const val REPRESENTATIVE_NOON_HOUR = 12
private const val MAX_FORECAST_DAYS = 5

/**
 * Groups OpenWeather 3-hour forecast intervals by local calendar date at [utcOffsetSeconds]
 * and aggregates true daily min/max temperatures plus daytime (06:00–22:00 local time)
 * wearing-window metrics.
 */
internal fun groupForecastByLocalDate(
    items: List<OpenWeatherForecastItemDto>,
    utcOffsetSeconds: Long,
    maxDays: Int = MAX_FORECAST_DAYS,
): List<ForecastDay> {
    if (items.isEmpty()) return emptyList()
    val zone = FixedOffsetTimeZone(UtcOffset(seconds = utcOffsetSeconds.toInt()))
    val withLocalTime =
        items.map { item ->
            val localDateTime = Instant.fromEpochSeconds(item.dt).toLocalDateTime(zone)
            item to localDateTime
        }
    return withLocalTime
        .groupBy { (_, localDateTime) -> localDateTime.date }
        .entries
        .sortedBy { (date, _) -> date }
        .take(maxDays)
        .map { (date, entries) -> aggregateForecastDay(date.toString(), entries) }
}

private fun aggregateForecastDay(
    label: String,
    entries: List<Pair<OpenWeatherForecastItemDto, LocalDateTime>>,
): ForecastDay {
    val allItems = entries.map { it.first }
    val daytimeEntries = entries.filter { (_, dt) -> dt.hour in DAYTIME_START_HOUR until DAYTIME_END_HOUR }
    val windowEntries = daytimeEntries.ifEmpty { entries }
    val windowItems = windowEntries.map { it.first }

    val minTemp = allItems.minOf { it.main.tempMin ?: it.main.temp }.roundToInt()
    val maxTemp = allItems.maxOf { it.main.tempMax ?: it.main.temp }.roundToInt()

    val feelsLikeValues = windowItems.mapNotNull { it.main.feelsLike }
    val windValues = windowItems.mapNotNull { it.wind?.speed }
    val humidityValues = windowItems.mapNotNull { it.main.humidity }
    val popValues = windowItems.mapNotNull { it.pop }
    val rainSum = windowItems.sumOf { it.rain?.threeHours ?: 0.0 }
    val representativeCondition = pickRepresentativeCondition(windowEntries)

    return ForecastDay(
        label = label,
        min = minTemp,
        max = maxTemp,
        icon = representativeCondition?.icon.orEmpty(),
        conditionId = representativeCondition?.id ?: 0,
        feelsLikeC = feelsLikeValues.takeIf { it.isNotEmpty() }?.average(),
        windSpeedMs = windValues.takeIf { it.isNotEmpty() }?.average(),
        humidity = humidityValues.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
        precipitationProbability = popValues.maxOrNull(),
        rainVolumeMm = rainSum,
    )
}

private fun pickRepresentativeCondition(windowEntries: List<Pair<OpenWeatherForecastItemDto, LocalDateTime>>): OpenWeatherConditionDto? =
    windowEntries
        .minByOrNull { (_, dt) -> abs(dt.hour - REPRESENTATIVE_NOON_HOUR) }
        ?.first
        ?.weather
        ?.firstOrNull()
