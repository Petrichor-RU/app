package nl.petrichor.app.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import nl.petrichor.app.AppConfig
import kotlin.math.roundToInt
import kotlin.time.Clock

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
