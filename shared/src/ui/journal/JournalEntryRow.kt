package nl.petrichor.app.ui.journal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.media.loadImagePainter
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
import nl.petrichor.app.ui.epochMsToLocalDate
import nl.petrichor.app.ui.isLaundryActive
import nl.petrichor.app.ui.longLabel
import nl.petrichor.app.ui.shortLabel

/** List item card displaying a single logged outfit entry, its pieces, laundry status, and survey action. */
@Composable
internal fun JournalEntryRow(
    entryWithPieces: OutfitEntryWithPieces,
    onClick: () -> Unit,
    onSurvey: (OutfitEntryWithPieces) -> Unit,
) {
    val entry = entryWithPieces.entry
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JournalEntryThumbnail(photoPath = entry.photoPath)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.wornOnDate.longLabel(), style = MaterialTheme.typography.titleMedium)
                if (entryWithPieces.pieces.isNotEmpty()) {
                    JournalPieceChips(pieces = entryWithPieces.pieces)
                }
                if (isLaundryActive(entry.laundryUntilEpochMs)) {
                    Text(
                        text = "In laundry until ${epochMsToLocalDate(entry.laundryUntilEpochMs).shortLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // Survey state/button sits at the end (right side) of the list item.
            if (entry.surveyFeelingScore != null && entry.surveyWarmthVerdict != null) {
                Text(
                    text = surveySummary(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedButton(
                    onClick = { onSurvey(entryWithPieces) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.CHECKLIST_RTL,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Survey")
                }
            }
        }
    }
}

@Composable
private fun JournalEntryThumbnail(photoPath: String?) {
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        val painter =
            if (photoPath?.isNotEmpty() == true) {
                loadImagePainter(photoPath)
            } else {
                null
            }
        if (painter != null) {
            Image(painter = painter, contentDescription = "Outfit selfie", modifier = Modifier.fillMaxSize())
        } else {
            MaterialSymbol(iconName = MaterialSymbols.IMAGE, contentDescription = null)
        }
    }
}

private fun surveySummary(entry: OutfitEntry): String =
    if (entry.surveyFeelingScore != null && entry.surveyWarmthVerdict != null) {
        "${entry.surveyFeelingScore}/5 - ${entry.surveyWarmthVerdict.verdictLabel()}"
    } else {
        "Survey pending"
    }
