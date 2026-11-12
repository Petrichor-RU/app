package nl.petrichor.app.ui.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import io.github.ismoy.imagepickerkmp.config.CropConfig
import io.github.ismoy.imagepickerkmp.config.GalleryConfig
import io.github.ismoy.imagepickerkmp.picker.ImagePickerKMPConfig
import io.github.ismoy.imagepickerkmp.picker.ImagePickerResult
import io.github.ismoy.imagepickerkmp.picker.rememberImagePickerKMP
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import nl.petrichor.app.data.media.MediaScope
import nl.petrichor.app.data.media.MediaStorage
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.ui.longLabel

private const val MAX_PAST_DAYS = 366
private const val MAX_FUTURE_DAYS = 5

/** Parameters for [AddOutfitDialog], bundled to keep the composable signature small. */
class AddOutfitDialogParams(
    val outfit: OutfitEntryWithPieces?,
    val clothingTypes: List<ClothingType>,
    val wardrobePieces: List<ClothingPiece>,
    val today: LocalDate,
    val onDismiss: () -> Unit,
    val onSave: suspend (date: LocalDate, photoPath: String?, notes: String?, selectedPieces: List<ClothingPiece>) -> Boolean,
)

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

/** Derived inputs for the dialog form body. */
private class AddOutfitFormInputs(
    val minDate: LocalDate,
    val maxDate: LocalDate,
    val piecesByType: List<Pair<ClothingType, List<ClothingPiece>>>,
    val currentPhotoPath: String?,
    val pickerResult: ImagePickerResult?,
    val selfieActions: SelfieActions,
)

/**
 * Dialog for logging an outfit (add) or editing a journal entry (edit, [AddOutfitDialogParams.outfit]
 * != null). Same shared add/edit pattern as AddClothingDialog. The selfie is optional; pieces are
 * a free multi-select (arbitrary subsets, each piece at most once).
 */
@Composable
fun AddOutfitDialog(params: AddOutfitDialogParams) {
    val state = remember { AddOutfitFormState(params) }

    // ImagePickerKMP rotate/crop is disabled everywhere (plan decision 20).
    val imagePicker =
        rememberImagePickerKMP(
            ImagePickerKMPConfig(
                galleryConfig = GalleryConfig(allowMultiple = false),
                cropConfig = CropConfig(enabled = false),
            ),
        )
    val selectedPhoto = (imagePicker.result as? ImagePickerResult.Success)?.first

    LaunchedEffect(selectedPhoto, state.useNewImage) {
        state.selectedPhotoPainter =
            if (state.useNewImage && selectedPhoto != null) {
                MediaStorage.loadPhotoPainter(selectedPhoto)
            } else {
                null
            }
    }

    val currentPhotoPath =
        when {
            state.useNewImage && selectedPhoto != null -> null

            // new photo will be copied on save
            state.isEditing && !state.removedPhoto -> params.outfit?.entry?.photoPath

            else -> null
        }

    val selfieActions =
        remember(imagePicker) {
            SelfieActions(
                onPickGallery = {
                    state.useNewImage = true
                    state.removedPhoto = false
                    imagePicker.launchGallery()
                },
                onPickCamera = {
                    state.useNewImage = true
                    state.removedPhoto = false
                    imagePicker.launchCamera()
                },
                onRemovePhoto = {
                    state.removedPhoto = true
                    state.useNewImage = false
                },
            )
        }

    AddOutfitDialogScaffold(
        state = state,
        params = params,
        currentPhotoPath = currentPhotoPath,
        pickerResult = imagePicker.result,
        selfieActions = selfieActions,
    )
}

@Composable
private fun AddOutfitDialogScaffold(
    state: AddOutfitFormState,
    params: AddOutfitDialogParams,
    currentPhotoPath: String?,
    pickerResult: ImagePickerResult?,
    selfieActions: SelfieActions,
) {
    val minDate = remember(params.today) { params.today.plus(-MAX_PAST_DAYS, DateTimeUnit.DAY) }
    val maxDate = remember(params.today) { params.today.plus(MAX_FUTURE_DAYS, DateTimeUnit.DAY) }
    val piecesByType =
        remember(params.wardrobePieces, params.clothingTypes) {
            params.clothingTypes.mapNotNull { type ->
                params.wardrobePieces
                    .filter { it.typeId == type.id }
                    .takeIf { it.isNotEmpty() }
                    ?.let { type to it }
            }
        }
    val inputs =
        AddOutfitFormInputs(
            minDate = minDate,
            maxDate = maxDate,
            piecesByType = piecesByType,
            currentPhotoPath = currentPhotoPath,
            pickerResult = pickerResult,
            selfieActions = selfieActions,
        )

    AlertDialog(
        onDismissRequest = { },
        title = { Text(if (state.isEditing) "Edit Outfit" else "Log Outfit") },
        text = { AddOutfitForm(state = state, inputs = inputs) },
        confirmButton = {
            SaveOutfitButton(
                state = state,
                params = params,
                imagePickerResult = pickerResult,
            )
        },
        dismissButton = {
            TextButton(onClick = params.onDismiss, enabled = !state.isSaving) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AddOutfitForm(
    state: AddOutfitFormState,
    inputs: AddOutfitFormInputs,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DateStepper(
            date = state.date,
            minDate = inputs.minDate,
            maxDate = inputs.maxDate,
            onDateChange = { state.date = it },
        )
        SelfieSection(
            state = state,
            currentPhotoPath = inputs.currentPhotoPath,
            pickerResult = inputs.pickerResult,
            actions = inputs.selfieActions,
        )
        PiecePicker(
            piecesByType = inputs.piecesByType,
            selectedPieceIds = state.selectedPieceIds,
            wardrobeEmpty = state.params.wardrobePieces.isEmpty(),
            onToggle = state::togglePiece,
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = { state.notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
        )
        state.saveError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DateStepper(
    date: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = { onDateChange(date.plus(-1, DateTimeUnit.DAY)) },
            enabled = date > minDate,
        ) {
            MaterialSymbol(iconName = MaterialSymbols.CHEVRON_LEFT, contentDescription = "Previous day")
        }
        Text(date.longLabel(), style = MaterialTheme.typography.titleMedium)
        IconButton(
            onClick = { onDateChange(date.plus(1, DateTimeUnit.DAY)) },
            enabled = date < maxDate,
        ) {
            MaterialSymbol(iconName = MaterialSymbols.CHEVRON_RIGHT, contentDescription = "Next day")
        }
    }
}

@Composable
private fun PiecePicker(
    piecesByType: List<Pair<ClothingType, List<ClothingPiece>>>,
    selectedPieceIds: Set<Long>,
    wardrobeEmpty: Boolean,
    onToggle: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Pieces (optional, ${selectedPieceIds.size} selected):",
            style = MaterialTheme.typography.bodySmall,
        )
        if (wardrobeEmpty) {
            Text(
                text = "No wardrobe pieces yet. Add some in the Wardrobe tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        piecesByType.forEach { (type, pieces) ->
            Text(
                text = type.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            pieces.forEach { piece ->
                PieceToggleRow(
                    piece = piece,
                    checked = piece.id in selectedPieceIds,
                    onToggle = { onToggle(piece.id) },
                )
            }
        }
    }
}

@Composable
private fun PieceToggleRow(
    piece: ClothingPiece,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(piece.name ?: "Piece ${piece.id}")
    }
}

@Composable
private fun SaveOutfitButton(
    state: AddOutfitFormState,
    params: AddOutfitDialogParams,
    imagePickerResult: ImagePickerResult?,
) {
    val coroutineScope = rememberCoroutineScope()
    TextButton(
        onClick = {
            state.isSaving = true
            state.saveError = null
            coroutineScope.launch {
                saveOutfit(state, params, imagePickerResult)
                state.isSaving = false
            }
        },
        enabled = !state.isSaving,
    ) {
        if (state.isSaving) {
            CircularProgressIndicator(Modifier.size(20.dp))
        } else {
            Text(if (state.isEditing) "Save Changes" else "Log Outfit")
        }
    }
}

@Suppress("TooGenericExceptionCaught") // platform image storage surfaces platform-specific exceptions as Exception in common code
private suspend fun saveOutfit(
    state: AddOutfitFormState,
    params: AddOutfitDialogParams,
    imagePickerResult: ImagePickerResult?,
): Boolean {
    val selectedPhoto = (imagePickerResult as? ImagePickerResult.Success)?.first
    val saved =
        try {
            val photoPath =
                when {
                    state.useNewImage && selectedPhoto != null -> MediaStorage.copyToPermanentStorage(selectedPhoto, MediaScope.OUTFIT)
                    state.isEditing && !state.removedPhoto -> params.outfit?.entry?.photoPath
                    else -> null
                }
            params.onSave(
                state.date,
                photoPath,
                state.notes.ifEmpty { null },
                params.wardrobePieces.filter { it.id in state.selectedPieceIds },
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                currentCoroutineContext().ensureActive()
            }
            state.saveError = "Could not save the outfit image: ${exception.message ?: "Unknown error"}"
            false
        }
    if (!saved && state.saveError == null) {
        state.saveError = "Could not save the outfit. Please try again."
    }
    return saved
}
