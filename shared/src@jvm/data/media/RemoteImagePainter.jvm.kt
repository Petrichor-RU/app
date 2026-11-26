package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.Painter

internal actual suspend fun loadRemoteImagePainter(url: String): Painter? =
    remoteImageBytes(url)?.let { bytes ->
        try {
            decodeExifOrientedImagePainter(bytes)
        } catch (_: Exception) {
            null
        }
    }
