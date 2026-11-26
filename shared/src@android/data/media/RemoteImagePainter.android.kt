package nl.petrichor.app.data.media

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter

/** Decoded remote painters are cached so scrolling does not re-download or re-decode. */
private val remotePainterCache = LruCache<String, Painter>(30)

internal actual suspend fun loadRemoteImagePainter(url: String): Painter? =
    remotePainterCache.get(url)
        ?: remoteImageBytes(url)?.let { bytes ->
            decodeRemoteBitmap(bytes)?.let { bitmap ->
                BitmapPainter(bitmap.asImageBitmap()).also { remotePainterCache.put(url, it) }
            }
        }

private fun decodeRemoteBitmap(bytes: ByteArray): android.graphics.Bitmap? =
    try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
