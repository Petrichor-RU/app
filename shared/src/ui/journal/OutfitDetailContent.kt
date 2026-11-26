package nl.petrichor.app.ui.journal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import nl.petrichor.app.ui.isLaundryActive
import nl.petrichor.app.ui.shortLabel

/**
 * Scrollable body of the outfit detail overlay, rendered by [OutfitDetailSheet] below its header
 * slot. Under 600 dp it is a single scrolling column (selfie above the details); on wider screens
 * it becomes two columns — fixed selfie beside an independently scrolling details column — so
 * short windows never trap the survey or laundry actions.
 *
 * @param header slot for the title/menu row supplied by the caller ([OutfitDetailSheet]).
 */
@Composable
internal fun OutfitDetailContent(
    entryWithPieces: OutfitEntryWithPieces,
    onSurvey: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val wideLayout = maxWidth >= 600.dp
        val scrollState = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(if (wideLayout) Modifier else Modifier.verticalScroll(scrollState))
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            header()
            if (wideLayout) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val imageSize = minOf(560.dp, (maxWidth - 24.dp) / 2, maxHeight)
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        SelfieCard(entryWithPieces.entry.photoPath, Modifier.size(imageSize))
                        EntryDetails(
                            entryWithPieces = entryWithPieces,
                            onSurvey = onSurvey,
                            modifier = Modifier.weight(1f).verticalScroll(scrollState),
                        )
                    }
                }
            } else {
                SelfieCard(entryWithPieces.entry.photoPath, Modifier.fillMaxWidth().aspectRatio(1f))
                EntryDetails(entryWithPieces, onSurvey)
            }
        }
    }
}

/** Notes, piece chips, weather snapshot, survey section and laundry notice for the entry. */
@Composable
private fun EntryDetails(
    entryWithPieces: OutfitEntryWithPieces,
    onSurvey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = entryWithPieces.entry
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        entry.notes?.let { notes ->
            Text(notes, style = MaterialTheme.typography.bodyMedium)
        }
        PiecesSection(pieces = entryWithPieces.pieces)
        WeatherSection(entry = entry)
        SurveySection(entry = entry, onSurvey = onSurvey)
        LaundryNotice(laundryUntilEpochMs = entry.laundryUntilEpochMs)
        Spacer(Modifier.height(8.dp))
    }
}

/** Hero card with the entry photo, or a placeholder icon when the entry has none. */
@Composable
private fun SelfieCard(
    photoPath: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
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

/** "Pieces" chips of the worn clothing; hidden when the entry has none. */
@Composable
private fun PiecesSection(pieces: List<ClothingPiece>) {
    if (pieces.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pieces", style = MaterialTheme.typography.titleMedium)
        JournalPieceChips(pieces = pieces)
    }
}

/** Weather at logging time (description, temperatures, rainy flag); hidden when the entry has none. */
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

/** Survey score/verdict with an edit-or-fill action through [onSurvey]; always shown. */
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

/** "In laundry until <date>" line; hidden once the until-time has passed or the entry was never marked. */
@Composable
private fun LaundryNotice(laundryUntilEpochMs: Long) {
    if (!isLaundryActive(laundryUntilEpochMs)) return
    Text(
        "In laundry until ${epochMsToLocalDate(laundryUntilEpochMs).shortLabel()}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
