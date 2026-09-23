package nl.petrichor.app.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int



class WeatherRepository(
    private val client: HttpClient = weatherHttpClient(),
    private val apiKey: String = WeatherConfig.openWeatherApiKey,
) {
    suspend fun load(place: Place): Result<WeatherSnapshot> = runCatching {
        require(apiKey.isNotBlank()) { "Add your OpenWeather API key to WeatherConfig.openWeatherApiKey." }
        val query = "lat=${place.coordinates.latitude}&lon=${place.coordinates.longitude}&units=metric&appid=$apiKey"
        val current = Json.parseToJsonElement(
            client.get("${WeatherConfig.weatherApiBase}/data/2.5/weather?$query").bodyAsText(),
        ).jsonObject
        val forecast = Json.parseToJsonElement(
            client.get("${WeatherConfig.weatherApiBase}/data/2.5/forecast?$query").bodyAsText(),
        ).jsonObject
        val currentWeather = current["weather"]!!.jsonArray.first().jsonObject
        val main = current["main"]!!.jsonObject
        val forecastItems = forecast["list"]!!.jsonArray
        WeatherSnapshot(
            place = place,
            temperature = main["temp"]!!.jsonPrimitive.double.toInt(),
            feelsLike = main["feels_like"]!!.jsonPrimitive.double.toInt(),
            description = currentWeather["description"]!!.jsonPrimitive.content.replaceFirstChar { it.uppercase() },
            icon = currentWeather["icon"]!!.jsonPrimitive.content,
            humidity = main["humidity"]!!.jsonPrimitive.int,
            windSpeed = current["wind"]!!.jsonObject["speed"]!!.jsonPrimitive.double.toInt(),
            forecast = forecastItems
                .filterIndexed { index, _ -> index % 8 == 0 }
                .take(5)
                .map { item ->
                    val itemObject = item.jsonObject
                    val itemMain = itemObject["main"]!!.jsonObject
                    ForecastDay(
                        label = itemObject["dt_txt"]!!.jsonPrimitive.content.substringBefore(" "),
                        min = itemMain["temp_min"]!!.jsonPrimitive.double.toInt(),
                        max = itemMain["temp_max"]!!.jsonPrimitive.double.toInt(),
                        icon = itemObject["weather"]!!.jsonArray.first().jsonObject["icon"]!!.jsonPrimitive.content,
                    )
                },
        )
    }
}
