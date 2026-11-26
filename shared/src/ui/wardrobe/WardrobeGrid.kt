package nl.petrichor.app.ui.wardrobe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.wardrobe.affiliate.SponsoredClothingCard
import nl.petrichor.app.ui.wardrobe.affiliate.WardrobeGridEntry

/**
 * The adaptive wardrobe grid of [WardrobeScreen]: the filter bar on top, the empty-wardrobe /
 * empty-filter message when there is nothing to show, and the merged user + sponsored entries
 * below. Sponsored cards fill the grid beneath the message with the same adaptive layout rules
 * as wardrobe items (two per row on phones, more when wide).
 */
@Composable
@Suppress("LongParameterList") // grid inputs are passed individually; grouping them would obscure the call site
internal fun WardrobeGrid(
    modifier: Modifier = Modifier,
    clothingTypes: List<ClothingType>,
    gridEntries: List<WardrobeGridEntry>,
    emptyMessage: String?,
    onTypeSelected: (Long?) -> Unit,
    onClothingClick: (Long) -> Unit,
    onSponsoredClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            WardrobeFilterBar(
                clothingTypes = clothingTypes,
                onTypeSelected = onTypeSelected,
                modifier = Modifier.statusBarsPadding(),
            )
        }

        // The empty-wardrobe / empty-filter message stays first and untouched; sponsored cards
        // fill the grid beneath it.
        if (emptyMessage != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BoxMessage(
                    message = emptyMessage,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(
            gridEntries,
            key = ::gridEntryKey,
        ) { entry ->
            when (entry) {
                is WardrobeGridEntry.UserItem -> {
                    ClothingCard(
                        clothing = entry.piece,
                        onClick = { onClothingClick(entry.piece.id) },
                        modifier = Modifier.aspectRatio(1f),
                    )
                }

                is WardrobeGridEntry.SponsoredItem -> {
                    SponsoredClothingCard(
                        item = entry.item,
                        onClick = { onSponsoredClick(entry.item.id) },
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
            }
        }
    }
}

private fun gridEntryKey(entry: WardrobeGridEntry): String =
    when (entry) {
        is WardrobeGridEntry.UserItem -> "clothing_${entry.piece.id}"
        is WardrobeGridEntry.SponsoredItem -> "sponsored_${entry.item.id}"
    }
