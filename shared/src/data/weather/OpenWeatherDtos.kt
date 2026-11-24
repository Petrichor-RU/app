package nl.petrichor.app.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenWeatherCurrentResponse(
    val dt: Long? = null,
    val weather: List<OpenWeatherConditionDto> = emptyList(),
    val main: OpenWeatherMainDto,
    val wind: OpenWeatherWindDto? = null,
    val rain: OpenWeatherRainDto? = null,
    val name: String? = null,
)

@Serializable
internal data class OpenWeatherForecastResponse(
    val list: List<OpenWeatherForecastItemDto> = emptyList(),
    val city: OpenWeatherCityDto? = null,
)

@Serializable
internal data class OpenWeatherForecastItemDto(
    val dt: Long,
    val main: OpenWeatherMainDto,
    val weather: List<OpenWeatherConditionDto> = emptyList(),
    val wind: OpenWeatherWindDto? = null,
    val pop: Double? = null,
    val rain: OpenWeatherRainDto? = null,
    @SerialName("dt_txt")
    val dtTxt: String? = null,
)

@Serializable
internal data class OpenWeatherMainDto(
    val temp: Double,
    @SerialName("feels_like")
    val feelsLike: Double? = null,
    @SerialName("temp_min")
    val tempMin: Double? = null,
    @SerialName("temp_max")
    val tempMax: Double? = null,
    val humidity: Int? = null,
)

@Serializable
internal data class OpenWeatherConditionDto(
    val id: Int = 0,
    val description: String = "",
    val icon: String = "",
)

@Serializable
internal data class OpenWeatherWindDto(
    val speed: Double = 0.0,
)

@Serializable
internal data class OpenWeatherRainDto(
    @SerialName("3h")
    val threeHours: Double = 0.0,
    @SerialName("1h")
    val oneHour: Double = 0.0,
)

@Serializable
internal data class OpenWeatherCityDto(
    val timezone: Long? = null,
    val name: String? = null,
)
