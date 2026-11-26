package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.Painter

// Cross-origin image fetches are usually blocked by CORS on WASM; remoteImageBytes returns null
// and the card falls back to a placeholder icon.
internal actual suspend fun loadRemoteImagePainter(url: String): Painter? =
    remoteImageBytes(url)?.let { bytes ->
        try {
            decodeImagePainter(bytes)
        } catch (_: Exception) {
            null
        }
    }
