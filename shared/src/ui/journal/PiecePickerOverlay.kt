@file:Suppress("MatchingDeclarationName") // hosts the overlay only, per project screen conventions

package nl.petrichor.app.ui.journal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.platform.BackHandler
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.wardrobe.ClothingCard
import nl.petrichor.app.ui.wardrobe.WardrobeFilterBar

/**
 * Full-screen gallery for picking wardrobe pieces for a journal entry. Mirrors the wardrobe
 * grid: pieces are shown as [ClothingCard]s with a type filter bar, and tapping a card
 * toggles its selection instead of opening the detail screen. Selection state is owned by
 * the caller ([selectedPieceIds]/[onToggle]) so it survives opening and closing.
 */
@Composable
@Suppress("LongMethod")
internal fun PiecePickerOverlay(
    pieces: List<ClothingPiece>,
    clothingTypes: List<ClothingType>,
    selectedPieceIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTypeId by remember { mutableStateOf<Long?>(null) }
    BackHandler(enabled = true, onBack = onDone)
    val filteredPieces =
        if (selectedTypeId == null) {
            pieces
        } else {
            pieces.filter { it.typeId == selectedTypeId }
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Select Pieces",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "${selectedPieceIds.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Confirm button: check marks while pieces are selected, close otherwise.
                IconButton(onClick = onDone) {
                    val hasSelection = selectedPieceIds.isNotEmpty()
                    AnimatedContent(
                        targetState = hasSelection,
                        transitionSpec = {
                            scaleIn(transformOrigin = TransformOrigin.Center) togetherWith
                                scaleOut(transformOrigin = TransformOrigin.Center)
                        },
                        label = "DoneIcon",
                    ) { withSelection ->
                        MaterialSymbol(
                            iconName = if (withSelection) MaterialSymbols.CHECK else MaterialSymbols.CLOSE,
                            contentDescription = "Done",
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WardrobeFilterBar(
                        clothingTypes = clothingTypes,
                        onTypeSelected = { typeId -> selectedTypeId = typeId },
                    )
                }
                if (pieces.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BoxMessage(
                            message = "Your wardrobe is empty. Add some pieces in the Wardrobe tab.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else if (filteredPieces.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BoxMessage(
                            message = "No items found for this type.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    items(filteredPieces) { piece ->
                        ClothingCard(
                            clothing = piece,
                            onClick = { onToggle(piece.id) },
                            isSelected = piece.id in selectedPieceIds,
                        )
                    }
                }
            }
        }
    }
}
