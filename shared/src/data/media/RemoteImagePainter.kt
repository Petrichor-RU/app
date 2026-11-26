package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.Painter
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import nl.petrichor.app.data.weather.weatherHttpClient
import kotlin.io.encoding.Base64

/**
 * Loads a remote https image into a [Painter]; null when the fetch or decode fails (no network,
 * CORS on WASM, non-image payload) — callers fall back to a placeholder icon.
 *
 * Suspend because the fetch is network I/O; call from a composition-scoped coroutine
 * (e.g. LaunchedEffect).
 */
internal expect suspend fun loadRemoteImagePainter(url: String): Painter?

/** Decodes already-fetched image [bytes] into a [Painter]; null when they are not a decodable image. */
internal expect suspend fun loadImageBytesPainter(bytes: ByteArray): Painter?

/**
 * Loads a bundled base64 image (e.g. a hand-picked static item's photo) into a [Painter] — no
 * network involved, so it also works on WASM and offline; null when the input is not valid
 * base64 or does not decode to an image.
 */
internal suspend fun loadBase64ImagePainter(base64: String): Painter? =
    decodeBase64Image(base64)?.let { bytes ->
        try {
            loadImageBytesPainter(bytes)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

/** Decodes a base64 string into bytes, ignoring any whitespace (chunked literals contain none, but be lenient); null when invalid. */
internal fun decodeBase64Image(base64: String): ByteArray? =
    try {
        Base64.decode(base64.filterNot { it.isWhitespace() })
    } catch (_: Exception) {
        null
    }

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
