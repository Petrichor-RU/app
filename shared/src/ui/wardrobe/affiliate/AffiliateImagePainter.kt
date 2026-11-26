package nl.petrichor.app.ui.wardrobe.affiliate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import nl.petrichor.app.data.affiliate.AffiliateItem
import nl.petrichor.app.data.media.loadBase64ImagePainter
import nl.petrichor.app.data.media.loadRemoteImagePainter

/**
 * Loads a sponsored item's photo into a [Painter]: the bundled base64 image when present
 * (hand-picked static items — no network, works offline and on WASM), otherwise the remote
 * [AffiliateItem.imageUrl]. Null when there is no image or loading fails (no network, CORS on
 * WASM, undecodable) — callers fall back to a placeholder icon.
 */
@Composable
internal fun rememberAffiliateImagePainter(item: AffiliateItem): Painter? {
    var painter by remember(item.imageBase64, item.imageUrl) { mutableStateOf<Painter?>(null) }
    LaunchedEffect(item.imageBase64, item.imageUrl) {
        painter =
            when {
                item.imageBase64 != null -> {
                    loadBase64ImagePainter(item.imageBase64)
                }

                item.imageUrl != null -> {
                    try {
                        loadRemoteImagePainter(item.imageUrl)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        ensureActive()
                        null // no network / CORS / undecodable: placeholder icon
                    }
                }

                else -> {
                    null
                }
            }
    }
    return painter
}
