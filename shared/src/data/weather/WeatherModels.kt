package nl.petrichor.app.data.weather

object WeatherConfig {
    const val openWeatherApiKey = ""
    const val weatherApiBase = "https://api.openweathermap.org"
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
