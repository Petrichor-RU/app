package nl.petrichor.app.data

import dev.jordond.compass.geolocation.Geolocator
import dev.jordond.compass.geolocation.browser
import nl.petrichor.app.data.weather.Coordinates

actual suspend fun currentCoordinates(): Coordinates? =
    Geolocator.browser().current().getOrNull()?.coordinates?.let { Coordinates(it.latitude, it.longitude) }
