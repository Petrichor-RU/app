package nl.petrichor.app.ui.recommendation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import kotlinx.coroutines.launch
import nl.petrichor.app.data.recommendation.OutfitGroup
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.ui.wardrobe.ClothingCard

/**
 * One labeled horizontal swipeable row of ClothingCards for an outfit group (plan section 7.2).
 * Each card has a laundry action that regenerates the suggestions.
 */
@Composable
internal fun CandidateGroupRow(
    group: OutfitGroup,
    onMarkLaundry: (ClothingPiece) -> Unit,
) {
    val pieces = group.pieces
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pieces.size.coerceAtLeast(1) })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pieces) {
        pagerState.scrollToPage(0)
    }

    fun step(delta: Int) {
        if (pieces.size > 1) {
            coroutineScope.launch {
                pagerState.animateScrollToPage((pagerState.currentPage + delta + pieces.size) % pieces.size)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = group.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(150.dp).nestedScroll(consumeHorizontalScroll),
        ) { page ->
            val piece = pieces.getOrNull(page)
            if (piece != null) {
                GroupCard(
                    piece = piece,
                    onClick = { step(1) },
                    onMarkLaundry = { onMarkLaundry(piece) },
                )
            }
        }
        if (pieces.size > 1) {
            Spacer(Modifier.height(2.dp))
            GroupPagerControls(
                pagerState = pagerState,
                pageCount = pieces.size,
                label = group.label,
                onStep = ::step,
            )
        }
    }
}

@Composable
private fun GroupCard(
    piece: ClothingPiece,
    onClick: () -> Unit,
    onMarkLaundry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ClothingCard(
            clothing = piece,
            onClick = onClick,
            onLaundryClick = onMarkLaundry,
            modifier = Modifier.width(140.dp),
        )
    }
}

@Composable
private fun GroupPagerControls(
    pagerState: PagerState,
    pageCount: Int,
    label: String,
    onStep: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onStep(-1) }, modifier = Modifier.size(32.dp)) {
            MaterialSymbol(
                iconName = MaterialSymbols.CHEVRON_LEFT,
                contentDescription = "Previous $label",
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount.coerceAtMost(5)) { index ->
                val isActive = index == pagerState.currentPage
                val dotWidth by animateDpAsState(
                    targetValue = if (isActive) 12.dp else 5.dp,
                    label = "group_dot_width",
                )
                val dotColor by animateColorAsState(
                    targetValue =
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                    label = "group_dot_color",
                )
                Box(
                    Modifier
                        .width(dotWidth)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
        IconButton(onClick = { onStep(1) }, modifier = Modifier.size(32.dp)) {
            MaterialSymbol(
                iconName = MaterialSymbols.CHEVRON_RIGHT,
                contentDescription = "Next $label",
            )
        }
    }
}
