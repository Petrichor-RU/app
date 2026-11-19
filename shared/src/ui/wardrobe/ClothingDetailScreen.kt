package nl.petrichor.app.ui.wardrobe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import nl.petrichor.app.data.media.loadImagePainter
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import nl.petrichor.app.data.wardrobe.WeatherProtection
import nl.petrichor.app.ui.epochMsToLocalDate
import nl.petrichor.app.ui.isLaundryActive
import nl.petrichor.app.ui.shortLabel

/**
 * Fullscreen detail screen for one wardrobe item, rendered by WardrobeScreen inside its detail
 * transition (state lives there, so the screen survives tab switches). Compact screens get a
 * single scrolling column (photo above the details); at >=600 dp the photo sits fixed beside an
 * independently scrolling details column. The FAB opens edit; the overflow menu handles laundry
 * and delete.
 */
@Composable
@Suppress("LongMethod", "LongParameterList") // full-screen overlay owned by the wardrobe tab
fun ClothingDetailScreen(
    clothing: ClothingPiece,
    clothingType: ClothingType,
    laundryUntilEpochMs: Long = 0,
    onDismiss: () -> Unit,
    onEdit: (ClothingPiece) -> Unit,
    onDelete: (ClothingPiece) -> Unit,
    onMarkLaundry: (days: Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showLaundryChooser by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEdit(clothing) },
                modifier = Modifier.padding(16.dp),
            ) {
                MaterialSymbol(
                    iconName = MaterialSymbols.EDIT,
                    contentDescription = "Edit clothing item",
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            val wideLayout = maxWidth >= 600.dp
            val contentScrollState = rememberScrollState()
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(if (wideLayout) Modifier else Modifier.verticalScroll(contentScrollState))
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ClothingDetailHeader(
                    clothing = clothing,
                    onDismiss = onDismiss,
                    onDelete = { onDelete(clothing) },
                    onShowLaundryChooser = { showLaundryChooser = true },
                )

                if (wideLayout) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        val imageSize = minOf(560.dp, (maxWidth - 24.dp) / 2, maxHeight)
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            ClothingDetailImage(clothing, Modifier.size(imageSize))
                            ClothingDetails(
                                clothing = clothing,
                                clothingType = clothingType,
                                laundryUntilEpochMs = laundryUntilEpochMs,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .verticalScroll(contentScrollState)
                                        .padding(bottom = 96.dp),
                            )
                        }
                    }
                } else {
                    ClothingDetailImage(clothing, Modifier.fillMaxWidth().aspectRatio(1f))
                    ClothingDetails(
                        clothing = clothing,
                        clothingType = clothingType,
                        laundryUntilEpochMs = laundryUntilEpochMs,
                        modifier = Modifier.padding(top = 8.dp, bottom = 96.dp),
                    )
                }
            }
        }
    }

    if (showLaundryChooser) {
        AlertDialog(
            onDismissRequest = { showLaundryChooser = false },
            title = { Text("Mark in laundry") },
            text = { Text("For how long?") },
            confirmButton = {
                TextButton(onClick = {
                    showLaundryChooser = false
                    onMarkLaundry(1)
                }) { Text("For 1 day") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLaundryChooser = false
                    onMarkLaundry(2)
                }) { Text("For 2 days") }
            },
        )
    }
}

/** Title row with the overflow menu (laundry/delete) and the close button. */
@Composable
private fun ClothingDetailHeader(
    clothing: ClothingPiece,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onShowLaundryChooser: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = clothing.name ?: "Clothing Item",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        ClothingDetailMenu(onDelete, onShowLaundryChooser)
        IconButton(onClick = onDismiss) {
            MaterialSymbol(
                iconName = MaterialSymbols.CLOSE,
                contentDescription = "Close",
            )
        }
    }
}

/** Overflow menu with "Mark in laundry" and "Delete". */
@Composable
private fun ClothingDetailMenu(
    onDelete: () -> Unit,
    onShowLaundryChooser: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            MaterialSymbol(
                iconName = MaterialSymbols.MORE_VERT,
                contentDescription = "More options",
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Mark in laundry") },
                onClick = {
                    showMenu = false
                    onShowLaundryChooser()
                },
                leadingIcon = {
                    MaterialSymbol(
                        iconName = MaterialSymbols.LOCAL_LAUNDRY_SERVICE,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    MaterialSymbol(
                        iconName = MaterialSymbols.DELETE,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}

/** Photo card for the item, or a placeholder icon when it has no image. */
@Composable
private fun ClothingDetailImage(
    clothing: ClothingPiece,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val painter =
                if (clothing.imagePath.isNotEmpty()) {
                    loadImagePainter(clothing.imagePath)
                } else {
                    null
                }

            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = clothing.name ?: "Clothing item",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                MaterialSymbol(
                    iconName = MaterialSymbols.IMAGE,
                    contentDescription = "Clothing image",
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

/** The "Details" rows (type, laundry, material, notes); scrolls independently in the wide layout. */
@Composable
private fun ClothingDetails(
    clothing: ClothingPiece,
    clothingType: ClothingType,
    laundryUntilEpochMs: Long,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Details",
            style = MaterialTheme.typography.titleMedium,
        )

        DetailRow(
            icon = MaterialSymbols.CATEGORY,
            label = "Type",
            value = clothingType.name,
        )

        if (isLaundryActive(laundryUntilEpochMs)) {
            DetailRow(
                icon = MaterialSymbols.LOCAL_LAUNDRY_SERVICE,
                label = "In laundry until",
                value = epochMsToLocalDate(laundryUntilEpochMs).shortLabel(),
            )
        }

        clothing.material.takeUnless { it == Material.UNKNOWN }?.let { material ->
            DetailRow(
                icon = MaterialSymbols.DESCRIPTION,
                label = "Material",
                value = material.displayName,
            )
        }

        clothing.weatherProtection.takeUnless { it == WeatherProtection.NONE }?.let { protection ->
            DetailRow(
                icon = MaterialSymbols.SHIELD,
                label = "Weather protection",
                value = protection.displayName,
            )
        }

        clothing.notes?.let { notes ->
            DetailRow(
                icon = MaterialSymbols.NOTES,
                label = "Notes",
                value = notes,
                isMultiline = true,
            )
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
    isMultiline: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MaterialSymbol(
            iconName = icon,
            contentDescription = label,
            modifier = Modifier.size(25.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Text(
                text = value,
                style = if (isMultiline) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                maxLines = if (isMultiline) Int.MAX_VALUE else 1,
                overflow = if (isMultiline) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }
    }
}
