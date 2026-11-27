package nl.petrichor.app.data.location

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.petrichor.app.AppConfig
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.weatherHttpClient

@Serializable
private data class GeoDto(
    val name: String,
    val state: String? = null,
    val country: String? = null,
    val lat: Double,
    val lon: Double,
)

private val json = Json { ignoreUnknownKeys = true }

class LocationSearchRepository(
    private val client: HttpClient = weatherHttpClient(),
    private val apiKey: String = AppConfig.OPENWEATHER_API_KEY,
) {
    suspend fun search(query: String): Result<List<LocationResult>> =
        runCatching {
            require(apiKey.isNotBlank()) {
                "Add your OpenWeather API key to AppConfig.OPENWEATHER_API_KEY (see README)."
            }
            val response =
                client.get("${AppConfig.OPENWEATHER_API_BASE}/geo/1.0/direct") {
                    parameter("q", query)
                    parameter("limit", 5)
                    parameter("appid", apiKey)
                }
            val body = response.bodyAsText()
            parseGeoResults(body)
        }

    /**
     * Reverse-geocodes [coordinates] to an ISO-3166 alpha-2 country code. Used to resolve the
     * country of the user's real device location and of legacy saved locations that predate
     * [nl.petrichor.app.data.weather.Place.countryCode]; failures return null and callers fall back (see data/affiliate).
     */
    suspend fun countryCodeFor(coordinates: Coordinates): Result<String?> =
        runCatching {
            require(apiKey.isNotBlank()) {
                "Add your OpenWeather API key to AppConfig.OPENWEATHER_API_KEY (see README)."
            }
            val response =
                client.get("${AppConfig.OPENWEATHER_API_BASE}/geo/1.0/reverse") {
                    parameter("lat", coordinates.latitude)
                    parameter("lon", coordinates.longitude)
                    parameter("limit", 1)
                    parameter("appid", apiKey)
                }
            val body = response.bodyAsText()
            parseReverseCountryCode(body)
        }
}

/**
 * Parses a geocoding response into location results, keeping only the first result per coordinate key so that
 * every result has a unique [LocationResult.key] (used as the LazyColumn item key and saved-location identity).
 */
internal fun parseGeoResults(body: String): List<LocationResult> =
    json
        .decodeFromString<List<GeoDto>>(body)
        .map { dto ->
            LocationResult(
                name = dto.name,
                region = listOfNotNull(dto.state, dto.country).joinToString(", "),
                coordinates = Coordinates(dto.lat, dto.lon),
                countryCode = dto.country?.uppercase(),
            )
        }.distinctBy { it.key }

/** Extracts the alpha-2 country code from a reverse-geocoding response; null when empty/unknown. */
internal fun parseReverseCountryCode(body: String): String? =
    json
        .decodeFromString<List<GeoDto>>(body)
        .firstOrNull()
        ?.country
        ?.uppercase()
