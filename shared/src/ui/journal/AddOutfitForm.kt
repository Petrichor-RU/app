package nl.petrichor.app.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import io.github.ismoy.imagepickerkmp.picker.ImagePickerResult
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.ui.longLabel

private const val MAX_PAST_DAYS = 366
private const val MAX_FUTURE_DAYS = 5

/** Scrollable form body for [AddOutfitDialog]. */
@Composable
internal fun AddOutfitForm(
    state: AddOutfitFormState,
    currentPhotoPath: String?,
    pickerResult: ImagePickerResult?,
    selfieActions: SelfieActions,
) {
    val today = state.params.today
    val minDate = remember(today) { today.plus(-MAX_PAST_DAYS, DateTimeUnit.DAY) }
    val maxDate = remember(today) { today.plus(MAX_FUTURE_DAYS, DateTimeUnit.DAY) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DateStepper(
            date = state.date,
            minDate = minDate,
            maxDate = maxDate,
            onDateChange = { state.date = it },
        )
        SelfieSection(
            state = state,
            currentPhotoPath = currentPhotoPath,
            pickerResult = pickerResult,
            actions = selfieActions,
        )
        val selectedPieces =
            remember(state.selectedPieceIds, state.params.wardrobePieces) {
                state.params.wardrobePieces.filter { it.id in state.selectedPieceIds }
            }
        PieceSummary(
            selectedPieces = selectedPieces,
            wardrobeEmpty = state.params.wardrobePieces.isEmpty(),
            onChoosePieces = { state.showPiecePicker = true },
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

/** Prompts opening the piece picker gallery and summarises the current selection as chips. */
@Composable
private fun PieceSummary(
    selectedPieces: List<ClothingPiece>,
    wardrobeEmpty: Boolean,
    onChoosePieces: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Pieces (optional, ${selectedPieces.size} selected):",
            style = MaterialTheme.typography.bodySmall,
        )
        if (wardrobeEmpty) {
            Text(
                text = "No wardrobe pieces yet. Add some in the Wardrobe tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = onChoosePieces,
            enabled = !wardrobeEmpty,
        ) {
            MaterialSymbol(
                iconName = MaterialSymbols.APPAREL,
                contentDescription = null,
                size = 18.dp,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Choose pieces")
        }
        if (selectedPieces.isNotEmpty()) {
            JournalPieceChips(pieces = selectedPieces)
        }
    }
}
