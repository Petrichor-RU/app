package nl.petrichor.app.data.weather

import kotlinx.serialization.Serializable

@Serializable
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

@Serializable // serialized into the saved_locations DataStore by SelectedLocationStore
data class Place(
    val name: String,
    val coordinates: Coordinates,
)

data class WeatherSnapshot(
    val place: Place,
    val temperature: Int,
    val feelsLike: Int,
    val description: String,
    val icon: String,
    /** OpenWeatherMap weather condition code (`weather[].id`, first entry is primary); see [WeatherConditions]. */
    val conditionId: Int,
    val humidity: Int,
    val windSpeed: Int,
    val forecast: List<ForecastDay>,
    /** UTC offset of the snapshot's location in seconds, from the forecast payload's `city.timezone`. */
    val utcOffsetSeconds: Long = 0,
    /**
     * Whether [utcOffsetSeconds] is known. The default assumes "unknown"; the repository sets it
     * from the payload's presence, so a real UTC+0 location still counts as known. Consumed by
     * BackgroundConditionResolver to compute the location's local time for the background.
     */
    val utcOffsetKnown: Boolean = utcOffsetSeconds != 0L,
)

data class ForecastDay(
    val label: String,
    val min: Int,
    val max: Int,
    val icon: String,
    /** OpenWeatherMap weather condition code (`weather[].id`, first entry is primary); see [WeatherConditions]. */
    val conditionId: Int,
)
