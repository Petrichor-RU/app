package nl.petrichor.app.data.location

import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place

expect suspend fun currentCoordinates(): Coordinates?

suspend fun currentPlace(): Place {
    // JVM has no geolocation, so the fallback is Amsterdam (NL).
    val device = currentCoordinates()
    val coordinates = device ?: Coordinates(52.3676, 4.9041)
    // A real device fix carries no hardcoded country — it is reverse-geocoded where needed
    // (see data/affiliate). Only the Amsterdam fallback is known to be NL.
    return Place("Current location", coordinates, countryCode = if (device != null) null else "NL")
}
