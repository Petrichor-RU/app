package nl.petrichor.app.ui.recommendation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import nl.petrichor.app.data.media.loadImagePainter
import nl.petrichor.app.data.recommendation.OutfitCandidate
import nl.petrichor.app.data.recommendation.RecommendationEngine
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.longLabel

/**
 * Horizontal pager over the top-3 outfit candidates (plan section 7.1). Each page shows the
 * dynamic zone groups as swipeable card rows, a selfie card for logged selfie-only outfits, or
 * the textual fallback suggestion.
 */
@Composable
internal fun CandidatePager(
    candidates: List<OutfitCandidate>,
    clothingTypes: List<ClothingType>,
    onMarkPieceLaundry: (pieceId: Long, days: Int) -> Unit,
) {
    if (candidates.isEmpty()) {
        BoxMessage(
            message = "No suggestions yet — add pieces in the Wardrobe tab or log an outfit.",
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { candidates.size })
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().nestedScroll(consumeHorizontalScroll),
        ) { page ->
            CandidatePage(
                candidate = candidates[page],
                typeById = clothingTypes.associateBy { it.id },
                onMarkPieceLaundry = onMarkPieceLaundry,
            )
        }
        if (candidates.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(candidates.size) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier =
                            Modifier
                                .size(if (active) 8.dp else 6.dp)
                                .background(
                                    if (active) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    },
                                    CircleShape,
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidatePage(
    candidate: OutfitCandidate,
    typeById: Map<Long, ClothingType>,
    onMarkPieceLaundry: (pieceId: Long, days: Int) -> Unit,
) {
    var laundryTarget by remember { mutableStateOf<ClothingPiece?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        candidate.loggedEntry?.let { logged ->
            Text(
                text = "From your journal — ${logged.entry.wornOnDate.longLabel()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (candidate.selfiePath != null) {
            SelfieCard(photoPath = candidate.selfiePath)
        }
        candidate.generalSuggestion?.let { suggestion ->
            RecommendationSlotCard(
                suggestion = suggestion,
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
        }
        val groups = RecommendationEngine.groupsFor(candidate.pieces, typeById)
        if (groups.isEmpty() && candidate.generalSuggestion == null && candidate.selfiePath == null) {
            BoxMessage(
                message = "No matching pieces in your wardrobe yet.",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        groups.forEach { group ->
            CandidateGroupRow(
                group = group,
                onMarkLaundry = { piece -> laundryTarget = piece },
            )
        }
    }
    laundryTarget?.let { piece ->
        PieceLaundryDialog(
            piece = piece,
            onDismiss = { laundryTarget = null },
            onChosen = { days ->
                laundryTarget = null
                onMarkPieceLaundry(piece.id, days)
            },
        )
    }
}

@Composable
private fun SelfieCard(photoPath: String) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
        shape = MaterialTheme.shapes.medium,
    ) {
        val painter = loadImagePainter(photoPath)
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = "Outfit selfie",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun PieceLaundryDialog(
    piece: ClothingPiece,
    onDismiss: () -> Unit,
    onChosen: (days: Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark in laundry") },
        text = { Text("${piece.name ?: "This piece"} will be left out of suggestions. For how long?") },
        confirmButton = {
            TextButton(onClick = { onChosen(1) }) { Text("For 1 day") }
        },
        dismissButton = {
            TextButton(onClick = { onChosen(2) }) { Text("For 2 days") }
        },
    )
}
