package nl.petrichor.app.data

import dev.jordond.compass.geolocation.Geolocator
import dev.jordond.compass.geolocation.mobile
import nl.petrichor.app.data.weather.Coordinates

actual suspend fun currentCoordinates(): Coordinates? =
    Geolocator.mobile().current().getOrNull()?.coordinates?.let { Coordinates(it.latitude, it.longitude) }
