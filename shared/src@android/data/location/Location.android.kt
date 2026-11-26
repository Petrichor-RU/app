package nl.petrichor.app.data.location

import dev.jordond.compass.geolocation.Geolocator
import dev.jordond.compass.geolocation.mobile
import dev.jordond.compass.geolocation.mobile.gms.GmsGeolocator
import dev.jordond.compass.geolocation.mobile.gms.isPlayServicesAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import nl.petrichor.app.data.weather.Coordinates
import kotlin.time.Duration.Companion.seconds

/** How long a single fix may take before giving up (the affiliate feed has its own timeout as backstop). */
private val FIX_TIMEOUT = 15.seconds

/**
 * The device's current coordinates, or null when unavailable. Prefers the Google Play Services
 * fused location provider ([GmsGeolocator]) — the platform `LocationManager` path is unreliable
 * on many devices — and falls back to `Geolocator.mobile()` when Play Services is absent.
 * Permission is requested by Compass on first use; denial/failure returns null (never throws).
 */
actual suspend fun currentCoordinates(): Coordinates? =
    try {
        withTimeout(FIX_TIMEOUT) {
            val geolocator =
                if (isPlayServicesAvailable()) {
                    GmsGeolocator()
                } else {
                    Geolocator.mobile()
                }
            geolocator
                .current()
                .getOrNull()
                ?.coordinates
                ?.let { Coordinates(it.latitude, it.longitude) }
        }
    } catch (_: TimeoutCancellationException) {
        null
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        null // any geolocation failure (permission denied, no provider, services off) degrades to null
    }
