package nl.petrichor.app.ui.wardrobe.affiliate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import nl.petrichor.app.data.affiliate.AffiliateItem
import nl.petrichor.app.data.media.loadRemoteImagePainter

/**
 * Full-screen detail overlay for a sponsored product, layout-compatible with
 * [nl.petrichor.app.ui.wardrobe.ClothingDetailScreen]: compact screens get a single scrolling column (photo above the
 * details); at >=600 dp the photo sits fixed on the left beside an independently scrolling
 * details' column. Rows: Store, Price (when present) and Type; the body carries the description
 * when present. The primary action, "Shop at <Store>", is handled by [onShop] — WardrobeScreen
 * opens the item's URL (already affiliate-tagged by the adapter) in the external browser via
 * PlatformBrowser.
 */
@Composable
@Suppress("LongMethod") // full-screen overlay: header, hero, rows, description, action
fun AffiliateDetailScreen(
    item: AffiliateItem,
    onDismiss: () -> Unit,
    onShop: (AffiliateItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var painter by remember(item.imageUrl) { mutableStateOf<Painter?>(null) }
    LaunchedEffect(item.imageUrl) {
        val url = item.imageUrl
        painter =
            if (url == null) {
                null
            } else {
                try {
                    loadRemoteImagePainter(url)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    ensureActive()
                    null
                }
            }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            val wideLayout = maxWidth >= 600.dp
            val contentScrollState = rememberScrollState()
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(if (wideLayout) Modifier else Modifier.verticalScroll(contentScrollState))
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AffiliateDetailHeader(item = item, onDismiss = onDismiss)

                if (wideLayout) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        val imageSize = minOf(560.dp, (maxWidth - 24.dp) / 2, maxHeight)
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            AffiliateDetailImage(item = item, painter = painter, modifier = Modifier.size(imageSize))
                            AffiliateDetailBody(
                                item = item,
                                onShop = onShop,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .verticalScroll(contentScrollState)
                                        .padding(bottom = 16.dp),
                            )
                        }
                    }
                } else {
                    AffiliateDetailImage(item = item, painter = painter, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                    AffiliateDetailBody(
                        item = item,
                        onShop = onShop,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }
}
