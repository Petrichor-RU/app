package nl.petrichor.app.ui.wardrobe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingType

/**
 * Fullscreen clothing detail overlay rendered by [WardrobeScreen] on top of the grid. The
 * screen passes the selected piece — or the closing piece while the exit animation finishes —
 * together with the transition state, so the closing animation plays in full before the
 * selection is cleared.
 */
@Composable
@Suppress("LongParameterList") // overlay inputs are passed individually; grouping them would obscure the call site
internal fun ClothingDetailOverlay(
    clothing: ClothingPiece?,
    clothingTypes: List<ClothingType>,
    transition: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (ClothingPiece) -> Unit,
    onMarkLaundry: (ClothingPiece, Int) -> Unit,
) {
    val clothingType = clothing?.let { piece -> clothingTypes.firstOrNull { it.id == piece.typeId } }
    if (clothing != null && clothingType != null) {
        AnimatedVisibility(
            visibleState = transition,
            modifier =
                Modifier
                    .fillMaxSize()
                    .zIndex(1f),
            enter =
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                ) + fadeIn(),
            exit =
                slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                ) + fadeOut(),
        ) {
            ClothingDetailScreen(
                clothing = clothing,
                clothingType = clothingType,
                laundryUntilEpochMs = clothing.laundryUntilEpochMs,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                onDismiss = onDismiss,
                onEdit = { onEdit() },
                onDelete = onDelete,
                onMarkLaundry = { days -> onMarkLaundry(clothing, days) },
            )
        }
    }
}
