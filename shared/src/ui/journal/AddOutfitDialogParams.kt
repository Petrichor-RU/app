package nl.petrichor.app.ui.journal

import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType

/** Parameters for [AddOutfitDialog], bundled to keep the composable signature small. */
class AddOutfitDialogParams(
    val outfit: OutfitEntryWithPieces?,
    val clothingTypes: List<ClothingType>,
    val wardrobePieces: List<ClothingPiece>,
    val today: LocalDate,
    val onDismiss: () -> Unit,
    val onSave: suspend (date: LocalDate, photoPath: String?, notes: String?, selectedPieces: List<ClothingPiece>) -> Boolean,
)
