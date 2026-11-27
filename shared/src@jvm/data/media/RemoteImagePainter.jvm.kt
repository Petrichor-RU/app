package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.Painter

internal actual suspend fun loadRemoteImagePainter(url: String): Painter? = remoteImageBytes(url)?.let { loadImageBytesPainter(it) }

internal actual suspend fun loadImageBytesPainter(bytes: ByteArray): Painter? =
    try {
        decodeExifOrientedImagePainter(bytes)
    } catch (_: Exception) {
        null
    }
