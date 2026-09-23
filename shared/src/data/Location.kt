package nl.petrichor.app.data

import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place

expect suspend fun currentCoordinates(): Coordinates?

suspend fun currentPlace(): Place {
    val coordinates = currentCoordinates() ?: Coordinates(52.3676, 4.9041)
    return Place("Current location", coordinates)
}
