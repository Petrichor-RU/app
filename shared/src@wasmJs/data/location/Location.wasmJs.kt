@file:OptIn(ExperimentalWasmJsInterop::class)

package nl.petrichor.app.data.location

import kotlinx.coroutines.suspendCancellableCoroutine
import nl.petrichor.app.data.weather.Coordinates
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private class LocationUnavailable : Exception()

internal external val navigator: BrowserNavigator?

internal external class BrowserNavigator {
    val geolocation: BrowserGeolocation?
}

internal external class BrowserGeolocation {
    fun getCurrentPosition(
        success: (BrowserPosition?) -> Unit,
        error: (BrowserPositionError) -> Unit,
        options: JsAny,
    )
}

internal external class BrowserPosition {
    val coords: BrowserCoordinates?
}

internal external class BrowserCoordinates {
    val latitude: Double
    val longitude: Double
}

internal external class BrowserPositionError

private fun createGeolocationOptions(): JsAny = js("({ enableHighAccuracy: false, timeout: 15000, maximumAge: 60000 })")

actual suspend fun currentCoordinates(): Coordinates? {
    val geolocation = navigator?.geolocation ?: return null
    return try {
        suspendCancellableCoroutine { continuation ->
            geolocation.getCurrentPosition(
                success = { position ->
                    val coords = position?.coords
                    if (coords != null) {
                        continuation.resume(Coordinates(coords.latitude, coords.longitude))
                    } else {
                        continuation.resumeWithException(LocationUnavailable())
                    }
                },
                error = { _ ->
                    continuation.resumeWithException(LocationUnavailable())
                },
                options = createGeolocationOptions(),
            )
        }
    } catch (_: Exception) {
        null
    }
}
