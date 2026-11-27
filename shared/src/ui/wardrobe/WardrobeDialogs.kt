package nl.petrichor.app.ui.wardrobe

import androidx.compose.runtime.Composable
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType

/**
 * The shared add/edit clothing dialogs of [WardrobeScreen]. [AddClothingDialog] backs both
 * flows: add starts empty, while edit starts from [editingClothing] and may retain or replace
 * its image.
 */
@Composable
@Suppress("LongParameterList") // dialog inputs are passed individually; grouping them would obscure the call site
internal fun WardrobeDialogs(
    showAddDialog: Boolean,
    showEditDialog: Boolean,
    editingClothing: ClothingPiece?,
    clothingTypes: List<ClothingType>,
    onDismissAdd: () -> Unit,
    onDismissEdit: () -> Unit,
    onSaveAdd: suspend (ClothingPiece) -> Unit,
    onSaveEdit: suspend (ClothingPiece) -> Unit,
) {
    if (showAddDialog) {
        AddClothingDialog(
            clothing = null,
            clothingTypes = clothingTypes,
            onDismiss = onDismissAdd,
            onSave = onSaveAdd,
        )
    }

    if (showEditDialog && editingClothing != null) {
        AddClothingDialog(
            clothing = editingClothing,
            clothingTypes = clothingTypes,
            onDismiss = onDismissEdit,
            onSave = onSaveEdit,
        )
    }
}
