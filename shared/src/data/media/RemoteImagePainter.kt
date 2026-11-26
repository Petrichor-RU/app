package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.Painter
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import nl.petrichor.app.data.weather.weatherHttpClient

/**
 * Loads a remote https image into a [Painter]; null when the fetch or decode fails (no network,
 * CORS on WASM, non-image payload) — callers fall back to a placeholder icon.
 *
 * Suspend because the fetch is network I/O; call from a composition-scoped coroutine
 * (e.g. LaunchedEffect).
 */
internal expect suspend fun loadRemoteImagePainter(url: String): Painter?

// Reuses the app-wide weather HTTP client (one shared engine); only the request URLs differ.
private val remoteImageClient: HttpClient by lazy { weatherHttpClient() }

/** Fetches [url]'s bytes; null on any failure except cancellation (image loads degrade to a placeholder). */
internal suspend fun remoteImageBytes(url: String): ByteArray? =
    try {
        remoteImageClient.get(url).bodyAsBytes()
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        null
    }
