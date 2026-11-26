package nl.petrichor.app.ui.wardrobe.affiliate

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.affiliate.AffiliateItem

/** Photo card for the product, or a placeholder icon when it has no (loadable) image. */
@Composable
internal fun AffiliateDetailImage(
    item: AffiliateItem,
    painter: Painter?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                MaterialSymbol(
                    iconName = MaterialSymbols.SHOPPING_BAG,
                    contentDescription = "Product image",
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}
