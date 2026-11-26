package nl.petrichor.app.ui.wardrobe.affiliate

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.affiliate.AffiliateItem

/**
 * Grid card for a sponsored product: square product photo (remote, or a bundled base64 image
 * for hand-picked items), a "Sponsored" badge at the top-start
 * (theme secondary container colour), no name overlay and no price on the card (price is
 * shown on the detail screen only).
 */
@Composable
fun SponsoredClothingCard(
    item: AffiliateItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val painter = rememberAffiliateImagePainter(item)
    Card(
        modifier =
            modifier
                .aspectRatio(1f)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val imagePainter = painter
            if (imagePainter != null) {
                Image(
                    painter = imagePainter,
                    contentDescription = "Sponsored: ${item.name}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                MaterialSymbol(
                    iconName = MaterialSymbols.SHOPPING_BAG,
                    contentDescription = "Sponsored: ${item.name}",
                    modifier = Modifier.fillMaxSize(0.5f),
                )
            }
            SponsoredBadge(modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

/** Compact corner tag marking the card as sponsored, in the theme's secondary container colour. */
@Composable
private fun SponsoredBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(6.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = "Sponsored",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
