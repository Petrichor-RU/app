package nl.petrichor.app.ui.wardrobe.affiliate

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
import nl.petrichor.app.data.affiliate.AffiliateItem

/**
 * Sponsored-item detail overlay rendered by [nl.petrichor.app.ui.wardrobe.WardrobeScreen] on top of the grid. The screen
 * keeps the selection until the exit transition has finished (see the settle effect in
 * [nl.petrichor.app.ui.wardrobe.WardrobeScreen]), so the closing animation plays in full.
 */
@Composable
internal fun AffiliateDetailOverlay(
    item: AffiliateItem?,
    transition: MutableTransitionState<Boolean>,
    onDismiss: () -> Unit,
    onShop: (AffiliateItem) -> Unit,
) {
    if (item != null) {
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
            AffiliateDetailScreen(
                item = item,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                onDismiss = onDismiss,
                onShop = onShop,
            )
        }
    }
}
