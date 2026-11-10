
package nl.petrichor.app.ui.outfitLog

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import io.github.ismoy.imagepickerkmp.config.GalleryConfig
import io.github.ismoy.imagepickerkmp.extensions.loadPainter
import io.github.ismoy.imagepickerkmp.picker.ImagePickerKMPConfig
import io.github.ismoy.imagepickerkmp.picker.ImagePickerResult
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import io.github.ismoy.imagepickerkmp.picker.rememberImagePickerKMP
import kotlinx.coroutines.launch
import nl.petrichor.app.data.outfitLog.OutfitLogRepository
import nl.petrichor.app.data.outfitLog.OutfitLogWithItems
import nl.petrichor.app.data.wardrobe.ClothingImageStorage
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.WardrobeRepository
import nl.petrichor.app.data.wardrobe.imageUriForLoading
import nl.petrichor.app.platform.isCameraAvailable
import kotlin.time.Instant
import androidx.compose.material3.TextButton

@Composable
fun OutfitLogScreen(
    modifier: Modifier = Modifier
) {
    val outfitRepository = remember { OutfitLogRepository() }
    val wardrobeRepository = remember { WardrobeRepository() }

    val logs by outfitRepository.getAllLogs()
        .collectAsStateWithLifecycle(emptyList())

    val clothing by wardrobeRepository.getAllClothing()
        .collectAsStateWithLifecycle(emptyList())

    var creatingLog by remember { mutableStateOf(false) }

    if (creatingLog) {
        CreateOutfitLogScreen(
            clothing = clothing,
            onBack = { creatingLog = false },
            onSave = { photoPath, rating, selectedIds ->
                outfitRepository.logOutfit(
                    photoPath = photoPath,
                    comfortRating = rating,
                    wardrobeClothingIds = selectedIds
                )
            },
            modifier = modifier
        )
    } else {
        OutfitHistoryScreen(
            logs = logs,
            onCreateLog = { creatingLog = true },
            modifier = modifier
        )
    }
}

@Composable
private fun CreateOutfitLogScreen(
    clothing: List<ClothingPiece>,
    onBack: () -> Unit,
    onSave: suspend (String, Int, List<Long>) -> Long,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    val imagePicker = rememberImagePickerKMP(
        ImagePickerKMPConfig(
            galleryConfig = GalleryConfig(allowMultiple = false)
        )
    )

    val selectedPhoto =
        (imagePicker.result as? ImagePickerResult.Success)?.first

    val photoPainter = selectedPhoto?.loadPainter()

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var comfortRating by remember { mutableFloatStateOf(7f) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }

            Text(
                "Log Outfit",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "1. Capture your outfit",
                    style = MaterialTheme.typography.titleMedium
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoPainter != null) {
                            Image(
                                painter = photoPainter,
                                contentDescription = "Outfit photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                MaterialSymbol(
                                    iconName = MaterialSymbols.IMAGE,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text("No photo selected")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { imagePicker.launchCamera() },
                        enabled = isCameraAvailable(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Take Photo")
                    }

                    OutlinedButton(
                        onClick = { imagePicker.launchGallery() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Gallery")
                    }
                }

                when (val result = imagePicker.result) {
                    is ImagePickerResult.Error -> {
                        Text(
                            "Image error: ${result.exception.message ?: "Unknown error"}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> Unit
                }
            }

            item {
                HorizontalDivider()

                Text(
                    "2. Select clothing items",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Select all the items you wore in this photo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (clothing.isEmpty()) {
                item {
                    Text("Your wardrobe is empty. Add clothing items first.")
                }
            } else {
                items(clothing, key = { it.id }) { item ->
                    ClothingSelectionRow(
                        clothing = item,
                        selected = item.id in selectedIds,
                        onClick = {
                            selectedIds = if (item.id in selectedIds) {
                                selectedIds - item.id
                            } else {
                                selectedIds + item.id
                            }
                        }
                    )
                }
            }

            item {
                HorizontalDivider()

                Text(
                    "3. Comfort rating",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "${comfortRating.toInt()} / 10",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Slider(
                    value = comfortRating,
                    onValueChange = {
                        comfortRating = it
                    },
                    valueRange = 1f..10f,
                    steps = 8
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Very uncomfortable")
                    Text("Very comfortable")
                }
            }

            item {
                errorMessage?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Button(
            onClick = {
                val photo = selectedPhoto ?: return@Button

                saving = true
                errorMessage = null

                scope.launch {
                    var savedPhotoPath: String? = null

                    try {
                        val path = ClothingImageStorage.copyToPermanentStorage(photo)
                        savedPhotoPath = path

                        onSave(
                            path,
                            comfortRating.toInt(),
                            selectedIds.toList()
                        )

                        onBack()
                    } catch (exception: Exception) {
                        savedPhotoPath?.let {
                            ClothingImageStorage.deleteImage(it)
                        }

                        errorMessage =
                            "Could not save outfit: ${exception.message ?: "Unknown error"}"
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            enabled = selectedPhoto != null &&
                    selectedIds.isNotEmpty() &&
                    !saving
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Save Outfit")
            }
        }
    }
}

@Composable
private fun ClothingSelectionRow(
    clothing: ClothingPiece,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (clothing.imagePath.isNotEmpty()) {
                val painter = PhotoResult(
                    uri = imageUriForLoading(clothing.imagePath)
                ).loadPainter()

                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = clothing.name,
                        modifier = Modifier.size(64.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    MaterialSymbol(
                        iconName = MaterialSymbols.IMAGE,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                }
            } else {
                MaterialSymbol(
                    iconName = MaterialSymbols.IMAGE,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    clothing.name ?: "Unnamed clothing",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    clothing.material.displayName,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() }
            )
        }
    }
}

@Composable
private fun OutfitHistoryScreen(
    logs: List<OutfitLogWithItems>,
    onCreateLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateLog) {
                MaterialSymbol(
                    iconName = MaterialSymbols.ADD,
                    contentDescription = "Log outfit"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Outfit History",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MaterialSymbol(
                            iconName = MaterialSymbols.CHECKROOM,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp)
                        )

                        Text(
                            "No outfits logged yet",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "Log what you wore today to start building your outfit history.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Button(
                            onClick = onCreateLog,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text("Log your first outfit")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(logs, key = { it.log.id }) { entry ->
                        OutfitHistoryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun OutfitHistoryCard(
    entry: OutfitLogWithItems
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val painter = PhotoResult(
                uri = imageUriForLoading(entry.log.photoPath)
            ).loadPainter()

            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = "Logged outfit",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Comfort",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "${entry.log.comfortRating}/10",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "Logged: ${Instant.fromEpochMilliseconds(entry.log.loggedAt)}",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "Clothing items",
                style = MaterialTheme.typography.titleSmall
            )

            entry.items.forEach { item ->
                Text(
                    "• ${item.clothingNameSnapshot ?: item.clothingTypeSnapshot}"
                )
            }
        }
    }
}
