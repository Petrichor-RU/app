package nl.petrichor.app.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import nl.petrichor.app.AppConfig
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

private const val DAYTIME_START_HOUR = 6
private const val DAYTIME_END_HOUR = 22
private const val REPRESENTATIVE_NOON_HOUR = 12
private const val MAX_FORECAST_DAYS = 5

private val openWeatherJson = Json { ignoreUnknownKeys = true }

class WeatherRepository(
    private val client: HttpClient = weatherHttpClient(),
    private val apiKey: String = AppConfig.OPENWEATHER_API_KEY,
    private val clock: Clock = Clock.System,
) {
    /**
     * Loads the current weather and 5-day forecast for [place] into one [WeatherSnapshot],
     * including the location's UTC offset ([WeatherSnapshot.utcOffsetSeconds]) that the
     * background scene uses for local time and that groups 3-hour forecast intervals by
     * local calendar date. Cancellation is rethrown so switching locations cancels the
     * in-flight request instead of swallowing it into a failure.
     */
    suspend fun load(place: Place): Result<WeatherSnapshot> =
        runCatching {
            require(apiKey.isNotBlank()) {
                "Add your OpenWeather API key to AppConfig.OPENWEATHER_API_KEY (see README)."
            }
            val query = "lat=${place.coordinates.latitude}&lon=${place.coordinates.longitude}&units=metric&appid=$apiKey"
            val currentBody = client.get("${AppConfig.OPENWEATHER_API_BASE}/data/2.5/weather?$query").bodyAsText()
            val forecastBody = client.get("${AppConfig.OPENWEATHER_API_BASE}/data/2.5/forecast?$query").bodyAsText()
            parseWeatherSnapshot(place, currentBody, forecastBody, clock.now().toEpochMilliseconds())
        }.onFailure { if (it is CancellationException) throw it }

    internal fun parseWeatherSnapshot(
        place: Place,
        currentBody: String,
        forecastBody: String,
        capturedAtEpochMs: Long = clock.now().toEpochMilliseconds(),
    ): WeatherSnapshot {
        val current = openWeatherJson.decodeFromString<OpenWeatherCurrentResponse>(currentBody)
        val forecast = openWeatherJson.decodeFromString<OpenWeatherForecastResponse>(forecastBody)
        return buildSnapshot(place, current, forecast, capturedAtEpochMs)
    }

    private fun buildSnapshot(
        place: Place,
        current: OpenWeatherCurrentResponse,
        forecast: OpenWeatherForecastResponse,
        capturedAtEpochMs: Long,
    ): WeatherSnapshot {
        val currentCondition = current.weather.firstOrNull()
        val utcOffsetSeconds = forecast.city?.timezone ?: 0L
        val utcOffsetKnown = forecast.city?.timezone != null
        val windSpeedMs = current.wind?.speed ?: 0.0
        val currentRain = current.rain?.let { if (it.oneHour > 0.0) it.oneHour else it.threeHours }
        return WeatherSnapshot(
            place = place,
            utcOffsetSeconds = utcOffsetSeconds,
            utcOffsetKnown = utcOffsetKnown,
            temperature = current.main.temp.roundToInt(),
            feelsLike = (current.main.feelsLike ?: current.main.temp).roundToInt(),
            description =
                currentCondition
                    ?.description
                    .orEmpty()
                    .replaceFirstChar { it.uppercase() },
            icon = currentCondition?.icon.orEmpty(),
            conditionId = currentCondition?.id ?: 0,
            humidity = current.main.humidity ?: 0,
            windSpeed = windSpeedMs.roundToInt(),
            forecast = groupForecastByLocalDate(forecast.list, utcOffsetSeconds),
            temperatureC = current.main.temp,
            feelsLikeC = current.main.feelsLike ?: current.main.temp,
            windSpeedMs = windSpeedMs,
            rainVolumeMm = currentRain,
            capturedAtEpochMs = capturedAtEpochMs,
        )
    }
}

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
