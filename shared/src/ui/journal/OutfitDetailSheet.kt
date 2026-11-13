package nl.petrichor.app.ui.journal

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.media.loadImagePainter
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.ui.epochMsToLocalDate
import nl.petrichor.app.ui.longLabel
import nl.petrichor.app.ui.shortLabel

/**
 * Full-screen overlay for a journal entry (plan section 5.3), reusing the visual pattern of
 * ClothingDetailScreen: hero selfie (or placeholder), date, notes, piece chips (archived
 * pieces marked), weather snapshot, survey section, and an overflow menu with laundry/edit/delete.
 */
@Composable
fun OutfitDetailSheet(
    entryWithPieces: OutfitEntryWithPieces,
    actions: OutfitDetailActions,
    modifier: Modifier = Modifier,
) {
    val entry = entryWithPieces.entry
    var showMenu by remember { mutableStateOf(false) }
    var showLaundryChooser by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailHeader(
                entry = entry,
                showMenu = showMenu,
                onMenuChange = { showMenu = it },
                onDismiss = actions.onDismiss,
                menuActions =
                    DetailMenuActions(
                        onEdit = { actions.onEdit(entryWithPieces) },
                        onDelete = { showDeleteConfirm = true },
                        onLaundry = { showLaundryChooser = true },
                    ),
            )
            SelfieCard(photoPath = entry.photoPath)
            entry.notes?.let { notes ->
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }
            PiecesSection(pieces = entryWithPieces.pieces)
            WeatherSection(entry = entry)
            SurveySection(entry = entry, onSurvey = { actions.onSurvey(entryWithPieces) })
            LaundryNotice(laundryUntilEpochMs = entry.laundryUntilEpochMs)
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showLaundryChooser) {
        LaundryChooserDialog(
            onDismiss = { showLaundryChooser = false },
            onChosen = { days ->
                showLaundryChooser = false
                actions.onMarkLaundry(entryWithPieces, days)
            },
        )
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                actions.onDelete(entryWithPieces)
            },
        )
    }
}

/** Overflow menu actions for [DetailHeader]. */
private class DetailMenuActions(
    val onEdit: () -> Unit,
    val onDelete: () -> Unit,
    val onLaundry: () -> Unit,
)

@Composable
private fun DetailHeader(
    entry: OutfitEntry,
    showMenu: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    menuActions: DetailMenuActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = entry.wornOnDate.longLabel(),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { onMenuChange(true) }) {
                MaterialSymbol(iconName = MaterialSymbols.MORE_VERT, contentDescription = "More options")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { onMenuChange(false) }) {
                DropdownMenuItem(
                    text = { Text("Mark outfit in laundry") },
                    onClick = {
                        onMenuChange(false)
                        menuActions.onLaundry()
                    },
                    leadingIcon = {
                        MaterialSymbol(iconName = MaterialSymbols.LOCAL_LAUNDRY_SERVICE, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        onMenuChange(false)
                        menuActions.onEdit()
                    },
                    leadingIcon = {
                        MaterialSymbol(iconName = MaterialSymbols.EDIT, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete entry", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        onMenuChange(false)
                        menuActions.onDelete()
                    },
                    leadingIcon = {
                        MaterialSymbol(iconName = MaterialSymbols.DELETE, contentDescription = null)
                    },
                )
            }
        }
        IconButton(onClick = onDismiss) {
            MaterialSymbol(iconName = MaterialSymbols.CLOSE, contentDescription = "Close")
        }
    }
}

@Composable
private fun SelfieCard(photoPath: String?) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val painter =
                if (photoPath?.isNotEmpty() == true) {
                    loadImagePainter(photoPath)
                } else {
                    null
                }
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = "Outfit selfie",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                MaterialSymbol(
                    iconName = MaterialSymbols.IMAGE,
                    contentDescription = "No selfie",
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

@Composable
private fun PiecesSection(pieces: List<ClothingPiece>) {
    if (pieces.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pieces", style = MaterialTheme.typography.titleMedium)
        JournalPieceChips(pieces = pieces)
    }
}

@Composable
private fun WeatherSection(entry: OutfitEntry) {
    if (entry.temperatureC == null && entry.minTempC == null && entry.weatherDescription == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Weather at logging", style = MaterialTheme.typography.titleMedium)
        entry.weatherDescription?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        val temps =
            listOfNotNull(
                entry.temperatureC?.let { "${it.toInt()}°" },
                entry.minTempC?.let { "min ${it.toInt()}°" },
                entry.maxTempC?.let { "max ${it.toInt()}°" },
            ).joinToString(" • ")
        if (temps.isNotEmpty()) {
            Text(temps, style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.isRainy) {
            Text("Rainy", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SurveySection(
    entry: OutfitEntry,
    onSurvey: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Survey", style = MaterialTheme.typography.titleMedium)
        if (entry.surveyFeelingScore != null && entry.surveyWarmthVerdict != null) {
            Text(
                "${entry.surveyFeelingScore}/5 - ${entry.surveyWarmthVerdict.verdictLabel()}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onSurvey) {
                Text("Edit survey")
            }
        } else {
            Text(
                "Survey pending",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onSurvey) {
                Text("Fill in survey")
            }
        }
    }
}

@Composable
private fun LaundryNotice(laundryUntilEpochMs: Long) {
    if (laundryUntilEpochMs <= 0) return
    Text(
        "In laundry until ${epochMsToLocalDate(laundryUntilEpochMs).shortLabel()}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LaundryChooserDialog(
    onDismiss: () -> Unit,
    onChosen: (days: Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark outfit in laundry") },
        text = { Text("For how long?") },
        confirmButton = {
            TextButton(onClick = { onChosen(1) }) { Text("For 1 day") }
        },
        dismissButton = {
            TextButton(onClick = { onChosen(2) }) { Text("For 2 days") }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete entry?") },
        text = { Text("This journal entry will be deleted.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
