package nl.petrichor.app.ui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.media.loadImagePainter
import nl.petrichor.app.data.wardrobe.ClothingPiece

/**
 * Card component for displaying a clothing item in the wardrobe.
 * When [onLaundryClick] is provided (recommendation screen), a laundry badge is shown
 * at the top right of the card.
 */
@Composable
fun ClothingCard(
    clothing: ClothingPiece,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLaundryClick: (() -> Unit)? = null,
) {
    Card(
        modifier =
            modifier
                .aspectRatio(1f)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val painter =
                if (clothing.imagePath.isNotEmpty()) {
                    loadImagePainter(clothing.imagePath)
                } else {
                    null
                }
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = clothing.name ?: "Clothing item",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                MaterialSymbol(
                    iconName = MaterialSymbols.IMAGE,
                    contentDescription = clothing.name ?: "Clothing item",
                    modifier = Modifier.fillMaxSize(0.5f),
                )
            }

            // Optional laundry badge at the top right (recommendation screen only)
            onLaundryClick?.let { onLaundry ->
                LaundryBadge(
                    pieceName = clothing.name,
                    onClick = onLaundry,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }

            // Optional name overlay at bottom
            clothing.name?.let { name ->
                NameOverlay(
                    name = name,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun NameOverlay(
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
    ) {
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun LaundryBadge(
    pieceName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(6.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol(
            iconName = MaterialSymbols.LOCAL_LAUNDRY_SERVICE,
            contentDescription = "Mark ${pieceName ?: "piece"} in laundry",
            modifier = Modifier.size(27.dp),
        )
    }
}
