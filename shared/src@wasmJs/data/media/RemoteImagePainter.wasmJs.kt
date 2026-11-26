package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.Painter

// Cross-origin image fetches are usually blocked by CORS on WASM; remoteImageBytes returns null
// and the card falls back to a placeholder icon. Bundled base64 images skip the network entirely.
internal actual suspend fun loadRemoteImagePainter(url: String): Painter? = remoteImageBytes(url)?.let { loadImageBytesPainter(it) }

internal actual suspend fun loadImageBytesPainter(bytes: ByteArray): Painter? =
    try {
        decodeImagePainter(bytes)
    } catch (_: Exception) {
        null
    }
