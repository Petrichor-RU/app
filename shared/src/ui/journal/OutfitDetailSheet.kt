package nl.petrichor.app.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.ui.longLabel

/**
 * Full-screen overlay for a journal entry, reusing the visual pattern of
 * ClothingDetailScreen: hero selfie (or placeholder), date, notes, piece chips (archived
 * pieces marked), weather snapshot, survey section, and an overflow menu with laundry/edit/delete.
 * The scrollable body is delegated to [OutfitDetailContent], which switches to a two-column layout
 * on wide screens; this composable owns the header menu and the laundry/delete confirmation dialogs.
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
    ) { paddingValues ->
        OutfitDetailContent(
            entryWithPieces = entryWithPieces,
            onSurvey = { actions.onSurvey(entryWithPieces) },
            modifier = Modifier.fillMaxSize().padding(paddingValues),
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
