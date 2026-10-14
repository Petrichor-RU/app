package nl.petrichor.app.data.location

import kotlinx.serialization.Serializable
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place

@Serializable
data class LocationResult(
    val name: String,
    val region: String = "",
    val coordinates: Coordinates,
) {
    val key: String
        get() = "${coordinates.latitude},${coordinates.longitude}"

    fun toPlace(): Place = Place(
        name = name,
        coordinates = Coordinates(coordinates.latitude, coordinates.longitude)
    )
}