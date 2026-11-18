package nl.petrichor.app.ui.journal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import io.github.ismoy.imagepickerkmp.picker.ImagePickerResult
import nl.petrichor.app.data.media.loadImagePainter
import nl.petrichor.app.platform.isCameraAvailable

@Composable
internal fun SelfieSection(
    state: AddOutfitFormState,
    currentPhotoPath: String?,
    pickerResult: ImagePickerResult?,
    actions: SelfieActions,
) {
    val showNewPhoto = state.useNewImage && state.selectedPhotoPainter != null
    SelfiePreviewBox(
        newPhotoPainter = if (showNewPhoto) state.selectedPhotoPainter else null,
        currentPhotoPath = currentPhotoPath,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = actions.onPickGallery,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (currentPhotoPath != null || showNewPhoto) "Replace photo" else "Select photo")
        }
        Button(
            onClick = actions.onPickCamera,
            enabled = isCameraAvailable(),
            modifier = Modifier.weight(1f),
        ) { Text("Camera") }
    }
    if (state.canRemovePhoto) {
        TextButton(onClick = actions.onRemovePhoto) {
            Text("Remove photo")
        }
    }
    PickerStatusText(pickerResult)
}

@Composable
internal fun SelfiePreviewBox(
    newPhotoPainter: Painter?,
    currentPhotoPath: String?,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.LightGray),
        contentAlignment = Alignment.Center,
    ) {
        when {
            newPhotoPainter != null -> {
                Image(
                    painter = newPhotoPainter,
                    contentDescription = "New outfit selfie",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            !currentPhotoPath.isNullOrEmpty() -> {
                val painter = loadImagePainter(currentPhotoPath)
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = "Current outfit selfie",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    MaterialSymbol(
                        iconName = MaterialSymbols.IMAGE,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.IMAGE,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Text("Selfie", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
internal fun PickerStatusText(result: ImagePickerResult?) {
    when (result) {
        is ImagePickerResult.Error -> {
            Text(
                text = "Could not select image: ${result.exception.message ?: "Unknown error"}",
                color = MaterialTheme.colorScheme.error,
            )
        }

        is ImagePickerResult.Loading -> {
            CircularProgressIndicator(Modifier.size(20.dp))
        }

        else -> {}
    }
}
