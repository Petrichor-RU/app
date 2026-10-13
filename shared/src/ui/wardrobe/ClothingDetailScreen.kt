package nl.petrichor.app.ui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import io.github.ismoy.imagepickerkmp.extensions.loadPainter
import io.github.ismoy.imagepickerkmp.picker.PhotoResult

/**
 * Fullscreen screen for showing clothing item details with edit and delete options.
 */
@Composable
fun ClothingDetailScreen(
    clothing: ClothingPiece,
    clothingType: ClothingType,
    onDismiss: () -> Unit,
    onEdit: (ClothingPiece) -> Unit,
    onDelete: (ClothingPiece) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(clothing) },
                modifier = Modifier.padding(16.dp)
            ) {
                MaterialSymbol(
                    iconName = MaterialSymbols.EDIT,
                    contentDescription = "Edit clothing item"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = clothing.name ?: "Clothing Item",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        MaterialSymbol(
                            iconName = MaterialSymbols.MORE_VERT,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete(clothing)
                            },
                            leadingIcon = {
                                MaterialSymbol(
                                    iconName = MaterialSymbols.DELETE,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.CLOSE,
                        contentDescription = "Close"
                    )
                }
            }


            // Clothing image
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = MaterialTheme.shapes.medium
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val painter = if (clothing.imagePath.isNotEmpty()) {
                        PhotoResult(uri = clothing.imagePath).loadPainter()
                    } else {
                        null
                    }

                    if (painter != null) {
                        Image(
                            painter = painter,
                            contentDescription = clothing.name ?: "Clothing item",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        MaterialSymbol(
                            iconName = MaterialSymbols.IMAGE,
                            contentDescription = "Clothing image",
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clothing details
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleMedium
                )

                DetailRow(
                    icon = MaterialSymbols.CATEGORY,
                    label = "Type",
                    value = clothingType.name
                )

                clothing.material.takeUnless { it == Material.UNKNOWN }?.let { material ->
                    DetailRow(
                        icon = MaterialSymbols.DESCRIPTION,
                        label = "Material",
                        value = material.displayName
                    )
                }

                clothing.notes?.let { notes ->
                    DetailRow(
                        icon = MaterialSymbols.NOTES,
                        label = "Notes",
                        value = notes,
                        isMultiline = true
                    )
                }
            }
        }
    }
}

/**
 * Helper composable for displaying a detail row with icon, label and value
 */
@Composable
private fun DetailRow(
    icon: String,
    label: String,
    value: String,
    isMultiline: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MaterialSymbol(
            iconName = icon,
            contentDescription = label,
            modifier = Modifier.size(25.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = if (isMultiline) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                maxLines = if (isMultiline) Int.MAX_VALUE else 1,
                overflow = if (isMultiline) TextOverflow.Clip else TextOverflow.Ellipsis
            )
        }
    }
}