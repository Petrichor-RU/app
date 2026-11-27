package nl.petrichor.app.data.guide

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.weatherHttpClient
import kotlin.time.Duration.Companion.seconds

private const val CLIMATE_API_BASE = "https://climate-api.open-meteo.com/v1/climate"

/*
 * The free tier grants 10,000 API calls per day, and a request's cost grows with the time
 * interval, the variables and the models: the full 1990-2020 range costs ~1,800 calls per
 * request, which exhausts the daily budget after only a handful of guide loads (HTTP 429).
 * A 7-year window (~400 calls) keeps the monthly normals stable while leaving room for many
 * locations per day.
 */
private const val CLIMATE_START_DATE = "2014-01-01"
private const val CLIMATE_END_DATE = "2020-12-31"
private const val MAX_RATE_LIMIT_RETRIES = 2
private const val DEFAULT_RETRY_AFTER_SECONDS = 30L
private const val MAX_RETRY_AFTER_SECONDS = 60L

/** Log tag; the project has no logging facade, so a tagged println lands in logcat/console everywhere. */
private const val LOG_TAG = "ClimateClient"

/** Raw daily series as returned by the Open-Meteo Climate API; nulls where the API omits a value. */
data class DailyClimateSeries(
    val dates: List<LocalDate>,
    val maxTemps: List<Double?>, // temperature_2m_max, °C
    val minTemps: List<Double?>, // temperature_2m_min, °C
    val precipitation: List<Double?>, // precipitation_sum, mm
)

@Serializable
private data class ClimateResponseDto(
    val daily: DailyDto? = null,
)

@Serializable
private data class DailyDto(
    val time: List<LocalDate> = emptyList(), // ISO dates, yyyy-MM-dd, parsed by LocalDate's default serializer
    @SerialName("temperature_2m_max") val maxTemps: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val minTemps: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipitation: List<Double?> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true }

/** Parses an Open-Meteo Climate API response body into a [DailyClimateSeries]; throws on malformed JSON. */
internal fun parseClimateResponse(body: String): DailyClimateSeries {
    val daily = json.decodeFromString<ClimateResponseDto>(body).daily ?: error("Climate response has no daily block.")
    return DailyClimateSeries(
        dates = daily.time,
        maxTemps = daily.maxTemps,
        minTemps = daily.minTemps,
        precipitation = daily.precipitation,
    )
}

/**
 * Aggregates a daily series into 12 monthly normals (pure function, unit-tested with fixtures):
 * - [MonthClimate.avgHigh]: mean of the month's `temperature_2m_max` values
 * - [MonthClimate.avgLow]: mean of the month's `temperature_2m_min` values
 * - [MonthClimate.precipitationMm]: the month's `precipitation_sum` total divided by the number of years
 * - [MonthClimate.humidity]: [humidity] for every month (the climate API has no humidity; 0 means unknown)
 *
 * Months without any daily values are zero-filled, so the result always has exactly 12 entries.
 */
internal fun aggregateToMonths(
    series: DailyClimateSeries,
    humidity: Int = 0,
): List<MonthClimate> {
    val indicesByMonth = series.dates.indices.groupBy { series.dates[it].month.number }
    return (1..12).map { month ->
        val indices = indicesByMonth[month].orEmpty()
        val highs = indices.mapNotNull { series.maxTemps.getOrNull(it) }
        val lows = indices.mapNotNull { series.minTemps.getOrNull(it) }
        val precipitation = indices.mapNotNull { series.precipitation.getOrNull(it) }
        val years = indices.map { series.dates[it].year }.toSet().size
        MonthClimate(
            month = month,
            avgHigh = highs.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            avgLow = lows.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            precipitationMm = if (years > 0) precipitation.sum() / years else 0.0,
            humidity = humidity,
        )
    }
}

/**
 * Client for the Open-Meteo Climate API (free, no API key). Reuses the platform [weatherHttpClient]
 * like the rest of the weather data layer; no `expect`/`actual` of its own. The aggregation of the
 * daily series into monthly normals is the pure [aggregateToMonths], separate from the HTTP call.
 */
class ClimateClient(
    private val client: HttpClient = weatherHttpClient(),
    private val baseUrl: String = CLIMATE_API_BASE,
) {
    /**
     * Fetches the ERA5 daily series for [place] over the [CLIMATE_START_DATE]–[CLIMATE_END_DATE]
     * window and aggregates it into 12 monthly normals. Non-success HTTP statuses fail the
     * [Result]; HTTP 429 (free-tier rate limit) is retried a few times with the server's
     * Retry-After (capped). Cancellation is rethrown so switching places cancels the in-flight
     * request instead of swallowing it into a failure.
     */
    suspend fun fetchMonths(
        place: Place,
        humidity: Int = 0,
    ): Result<List<MonthClimate>> =
        runCatching {
            val response = getWithRateLimitRetry(place)
            if (!response.status.isSuccess()) {
                // Most often HTTP 429: the free tier's daily call budget is spent for this IP.
                val details = rateLimitDetails(response)
                println("$LOG_TAG Climate API request for ${place.name} failed with HTTP ${response.status.value}. $details")
                error("Climate API returned HTTP ${response.status.value}.")
            }
            aggregateToMonths(parseClimateResponse(response.bodyAsText()), humidity)
        }.onFailure { if (it is CancellationException) throw it }

    /** GETs the climate series, retrying when the free tier rate-limits the heavy request (HTTP 429). */
    private suspend fun getWithRateLimitRetry(place: Place): HttpResponse {
        var response = request(place)
        var retries = 0
        while (response.status == HttpStatusCode.TooManyRequests && retries < MAX_RATE_LIMIT_RETRIES) {
            val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()
            val waitSeconds = (retryAfter ?: DEFAULT_RETRY_AFTER_SECONDS).coerceAtMost(MAX_RETRY_AFTER_SECONDS)
            val details = rateLimitDetails(response)
            val attempt = "attempt ${retries + 1}/$MAX_RATE_LIMIT_RETRIES"
            println("$LOG_TAG Climate API rate limited (HTTP 429) for ${place.name}; $details; retrying in ${waitSeconds}s ($attempt)")
            response.bodyAsText() // consume the body so the connection is released before retrying
            delay(waitSeconds.seconds)
            response = request(place)
            retries++
        }
        return response
    }

    /** The rate-limit headers the API sends, for diagnosing 429s in the logs. */
    private fun rateLimitDetails(response: HttpResponse): String {
        fun header(name: String) = response.headers[name]?.let { "$name=$it" }
        return listOfNotNull(
            header(HttpHeaders.RetryAfter),
            header("X-RateLimit-Limit"),
            header("X-RateLimit-Remaining"),
            header("X-RateLimit-Reset"),
            header("X-RateLimit-Window"),
        ).joinToString(separator = ", ").ifEmpty { "no rate-limit headers" }
    }

    private suspend fun request(place: Place): HttpResponse =
        client.get(baseUrl) {
            parameter("latitude", place.coordinates.latitude)
            parameter("longitude", place.coordinates.longitude)
            parameter("start_date", CLIMATE_START_DATE)
            parameter("end_date", CLIMATE_END_DATE)
            parameter("models", "era5")
            parameter("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum")
        }
}
