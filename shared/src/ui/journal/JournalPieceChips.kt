package nl.petrichor.app.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.wardrobe.ClothingPiece

/** Small chip used for piece names in journal rows and the detail sheet. */
@Composable
internal fun JournalPieceChips(
    pieces: List<ClothingPiece>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        pieces.forEach { piece ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .background(
                            if (piece.archived) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = MaterialTheme.shapes.small,
                        ).padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                if (piece.archived) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.LOCK,
                        contentDescription = "Archived piece",
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = piece.name ?: "Piece ${piece.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (piece.archived) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}
