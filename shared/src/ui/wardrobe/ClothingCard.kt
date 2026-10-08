package nl.petrichor.app.ui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.wardrobe.ClothingPiece
import io.github.ismoy.imagepickerkmp.extensions.loadPainter
import io.github.ismoy.imagepickerkmp.picker.PhotoResult

/**
 * Card component for displaying a clothing item in the wardrobe
 */
@Composable
fun ClothingCard(
    clothing: ClothingPiece,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val painter = if (clothing.imagePath.isNotEmpty()) {
                PhotoResult(uri = clothing.imagePath).loadPainter()
            } else {
                null
            }
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = clothing.name ?: "Clothing item",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                MaterialSymbolsRenderingScope {
                    MaterialSymbol(
                        iconName = MaterialSymbols.IMAGE,
                        contentDescription = clothing.name ?: "Clothing item",
                        modifier = Modifier.fillMaxSize(0.5f)
                    )
                }
            }

            // Optional name overlay at bottom
            clothing.name?.let { name ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        )
                ) {
                    Text(
                        text = name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}