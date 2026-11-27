package nl.petrichor.app.ui.wardrobe

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.ui.wardrobe.affiliate.WardrobeGridEntry

/**
 * Scaffold-level content of [WardrobeScreen]: the [WardrobeGrid] with its filter bar, the
 * snackbar host and the add button. The detail overlays and dialogs are rendered by the screen
 * on top of this.
 */
@Composable
@Suppress("LongParameterList") // content inputs are passed individually; grouping them would obscure the call site
internal fun WardrobeContent(
    snackbarHostState: SnackbarHostState,
    clothingTypes: List<ClothingType>,
    gridEntries: List<WardrobeGridEntry>,
    emptyMessage: String?,
    showFab: Boolean,
    onAddClick: () -> Unit,
    onTypeSelected: (Long?) -> Unit,
    onClothingClick: (Long) -> Unit,
    onSponsoredClick: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = onAddClick,
                ) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.ADD,
                        contentDescription = "Add clothing",
                    )
                }
            }
        },
    ) { paddingValues ->
        WardrobeGrid(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            clothingTypes = clothingTypes,
            gridEntries = gridEntries,
            emptyMessage = emptyMessage,
            onTypeSelected = onTypeSelected,
            onClothingClick = onClothingClick,
            onSponsoredClick = onSponsoredClick,
        )
    }
}
