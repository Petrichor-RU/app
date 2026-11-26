package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

internal actual suspend fun loadRemoteImagePainter(url: String): Painter? =
    remoteImageBytes(url)?.let { bytes ->
        try {
            BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
        } catch (_: Exception) {
            null
        }
    }
