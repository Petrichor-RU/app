package nl.petrichor.app.ui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingImageStorage
import nl.petrichor.app.data.wardrobe.ClothingType
import io.github.ismoy.imagepickerkmp.config.CropConfig
import io.github.ismoy.imagepickerkmp.config.GalleryConfig
import io.github.ismoy.imagepickerkmp.extensions.loadPainter
import io.github.ismoy.imagepickerkmp.picker.ImagePickerKMPConfig
import io.github.ismoy.imagepickerkmp.picker.ImagePickerResult
import io.github.ismoy.imagepickerkmp.picker.rememberImagePickerKMP
import kotlinx.coroutines.launch

/**
 * Dialog for adding/editing clothing items
 */
@Composable
fun AddClothingDialog(
    onDismiss: () -> Unit,
    onSave: (ClothingPiece) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ClothingType.T_SHIRT) }
    var material by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var showTypeMenu by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val imagePicker = rememberImagePickerKMP(
        ImagePickerKMPConfig(
            galleryConfig = GalleryConfig(allowMultiple = false),
            cropConfig = CropConfig(enabled = true, squareCrop = true, circularCrop = false)
        )
    )
    val selectedPhoto = (imagePicker.result as? ImagePickerResult.Success)?.first

    MaterialSymbolsRenderingScope {
        AlertDialog(
            onDismissRequest = {  },
            title = { Text("Add Clothing Item") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedPhoto == null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MaterialSymbol(
                                    iconName = MaterialSymbols.IMAGE,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text("Add a clothing photo", color = Color.Gray)
                            }
                        } else {
                            selectedPhoto.loadPainter()?.let { painter ->
                                Image(
                                    painter = painter,
                                    contentDescription = "Selected clothing photo",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { imagePicker.launchGallery() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Gallery") }
                        Button(
                            onClick = { imagePicker.launchCamera() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Camera") }
                    }

                    when (val result = imagePicker.result) {
                        is ImagePickerResult.Error ->
                            Text(
                                text = "Could not select image: ${result.exception.message ?: "Unknown error"}",
                                color = MaterialTheme.colorScheme.error
                            )
                        is ImagePickerResult.Loading -> CircularProgressIndicator(Modifier.size(20.dp))
                        else -> Unit
                    }
                    saveError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }

                    // Name field
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Type selection with dropdown
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Type:", style = MaterialTheme.typography.bodySmall)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = selectedType.displayName,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showTypeMenu = true }) {
                                MaterialSymbol(
                                    iconName = MaterialSymbols.ARROW_DROP_DOWN,
                                    contentDescription = "Select type"
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showTypeMenu,
                                onDismissRequest = { showTypeMenu = false }
                            ) {
                                ClothingType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.displayName) },
                                        onClick = {
                                            selectedType = type
                                            showTypeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Material field
                    OutlinedTextField(
                        value = material,
                        onValueChange = { material = it },
                        label = { Text("Material (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Notes field
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isSaving = true
                        saveError = null
                        coroutineScope.launch {
                            try {
                                val imagePath = selectedPhoto?.let {
                                    ClothingImageStorage.copyToPermanentStorage(it)
                                }.orEmpty()
                                onSave(
                                    ClothingPiece(
                                        name = name.ifEmpty { null },
                                        type = selectedType,
                                        material = material.ifEmpty { null },
                                        notes = notes.ifEmpty { null },
                                        imagePath = imagePath,
                                        thumbnailPath = imagePath
                                    )
                                )
                                isSaving = false
                            } catch (exception: Exception) {
                                saveError = "Could not save clothing image: ${exception.message ?: "Unknown error"}"
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                    } else {
                        Text("Save")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}