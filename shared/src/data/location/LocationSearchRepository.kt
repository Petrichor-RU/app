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
    suspend fun search(query: String): Result<List<LocationResult>> = runCatching {
        require(apiKey.isNotBlank()) { "Add your OpenWeather API key to AppConfig.OpenWeatherApiKey." }
        val response = client.get("${AppConfig.OPENWEATHER_API_BASE}/geo/1.0/direct") {
            parameter("q", query)
            parameter("limit", 5)
            parameter("appid", apiKey)
        }
        json.decodeFromString<List<GeoDto>>(response.bodyAsText())
            .map { dto ->
                LocationResult(
                    name = dto.name,
                    region = listOfNotNull(dto.state, dto.country).joinToString(", "),
                    coordinates = Coordinates(dto.lat, dto.lon),
                )
            }
            .distinctBy { it.name to it.region }
    }
}