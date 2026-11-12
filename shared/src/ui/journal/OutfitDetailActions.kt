package nl.petrichor.app.ui.journal

import nl.petrichor.app.data.outfit.OutfitEntryWithPieces

/** Callbacks for [OutfitDetailSheet], bundled to keep the composable signature small. */
class OutfitDetailActions(
    val onDismiss: () -> Unit,
    val onEdit: (OutfitEntryWithPieces) -> Unit,
    val onDelete: (OutfitEntryWithPieces) -> Unit,
    val onSurvey: (OutfitEntryWithPieces) -> Unit,
    val onMarkLaundry: (OutfitEntryWithPieces, days: Int) -> Unit,
)
