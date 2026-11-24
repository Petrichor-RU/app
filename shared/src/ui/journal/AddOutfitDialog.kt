package nl.petrichor.app.ui.journal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.ismoy.imagepickerkmp.config.CropConfig
import io.github.ismoy.imagepickerkmp.config.GalleryConfig
import io.github.ismoy.imagepickerkmp.picker.ImagePickerKMPConfig
import io.github.ismoy.imagepickerkmp.picker.ImagePickerResult
import io.github.ismoy.imagepickerkmp.picker.rememberImagePickerKMP
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nl.petrichor.app.data.media.MediaScope
import nl.petrichor.app.data.media.MediaStorage

/**
 * Dialog for logging an outfit (add) or editing a journal entry (edit, [AddOutfitDialogParams.outfit]
 * != null). Same shared add/edit pattern as AddClothingDialog. The selfie is optional; pieces are
 * a free multi-select (arbitrary subsets, each piece at most once).
 */
@Composable
fun AddOutfitDialog(params: AddOutfitDialogParams) {
    val state = remember { AddOutfitFormState(params) }

    // ImagePickerKMP rotate/crop is disabled everywhere.
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

    if (state.showPiecePicker) {
        PiecePickerDialog(state = state, params = params)
    }

    AddOutfitDialogScaffold(
        state = state,
        params = params,
        currentPhotoPath = currentPhotoPath,
        pickerResult = imagePicker.result,
        selfieActions = selfieActions,
    )
}

/** Opens the piece picker in its own full-screen window, keeping the dialog state intact underneath. */
@Composable
private fun PiecePickerDialog(
    state: AddOutfitFormState,
    params: AddOutfitDialogParams,
) {
    Dialog(
        onDismissRequest = { state.showPiecePicker = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        PiecePickerOverlay(
            pieces = params.wardrobePieces,
            clothingTypes = params.clothingTypes,
            selectedPieceIds = state.selectedPieceIds,
            onToggle = state::togglePiece,
            onDone = { state.showPiecePicker = false },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AddOutfitDialogScaffold(
    state: AddOutfitFormState,
    params: AddOutfitDialogParams,
    currentPhotoPath: String?,
    pickerResult: ImagePickerResult?,
    selfieActions: SelfieActions,
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(if (state.isEditing) "Edit Outfit" else "Log Outfit") },
        text = {
            AddOutfitForm(
                state = state,
                currentPhotoPath = currentPhotoPath,
                pickerResult = pickerResult,
                selfieActions = selfieActions,
            )
        },
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
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            state.saveError = "Could not save the outfit image: ${exception.message ?: "Unknown error"}"
            false
        }
    if (!saved && state.saveError == null) {
        state.saveError = "Could not save the outfit. Please try again."
    }
    return saved
}
