package nl.petrichor.app.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter

/** Mutable form state for [AddOutfitDialog]. */
internal class AddOutfitFormState(
    val params: AddOutfitDialogParams,
) {
    val isEditing = params.outfit != null
    var date by mutableStateOf(params.outfit?.entry?.wornOnDate ?: params.today)
    var notes by mutableStateOf(
        params.outfit
            ?.entry
            ?.notes
            .orEmpty(),
    )
    var selectedPieceIds by mutableStateOf(
        params.outfit
            ?.pieces
            ?.map { it.id }
            ?.toSet() ?: emptySet<Long>(),
    )
    var showPiecePicker by mutableStateOf(false)
    var useNewImage by mutableStateOf(false)
    var removedPhoto by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var saveError by mutableStateOf<String?>(null)
    var selectedPhotoPainter by mutableStateOf<Painter?>(null)

    val canRemovePhoto: Boolean
        get() =
            isEditing && !removedPhoto &&
                !params.outfit
                    ?.entry
                    ?.photoPath
                    .isNullOrEmpty()

    fun togglePiece(id: Long) {
        selectedPieceIds =
            if (id in selectedPieceIds) {
                selectedPieceIds - id
            } else {
                selectedPieceIds + id
            }
    }
}
