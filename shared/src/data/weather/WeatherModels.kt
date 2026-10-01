package nl.petrichor.app.data.weather

/**
 * Configuration for Weather API settings.
 * These values can be overridden per platform or via environment variables.
 */
object WeatherConfig {
    // Default values that can be overridden
    var openWeatherApiKey: String = System.getenv("OPEN_WEATHER_API_KEY") ?: "32c4c52a937c29140a466c2fdbce80f7"
    var weatherApiBase: String = System.getenv("WEATHER_API_BASE") ?: "https://api.openweathermap.org"
}

data class Coordinates(val latitude: Double, val longitude: Double)
data class Place(val name: String, val coordinates: Coordinates)
data class WeatherSnapshot(
    val place: Place,
    val temperature: Int,
    val feelsLike: Int,
    val description: String,
    val icon: String,
    val humidity: Int,
    val windSpeed: Int,
    val forecast: List<ForecastDay>,
)
data class ForecastDay(val label: String, val min: Int, val max: Int, val icon: String)
