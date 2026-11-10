package nl.petrichor.app.ui.recommendation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
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
import nl.petrichor.app.data.recommendation.LayerRecommendation
import nl.petrichor.app.ui.wardrobe.ClothingCard

@Composable
internal fun SwipeableClothingSlot(
    layer: LayerRecommendation,
    showLayerLabel: Boolean,
) {
    val optionCount = layer.options.size
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { optionCount.coerceAtLeast(1) })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(layer.options) {
        pagerState.scrollToPage(0)
    }

    fun stepOption(delta: Int) {
        if (optionCount > 1) {
            coroutineScope.launch {
                pagerState.animateScrollToPage((pagerState.currentPage + delta + optionCount) % optionCount)
            }
        }
    }

    Column(
        modifier = Modifier.width(136.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showLayerLabel) {
            Text(
                text = layer.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.size(132.dp).nestedScroll(consumeHorizontalScroll),
        ) { page ->
            val clothing = layer.options.getOrNull(page)
            if (clothing != null) {
                ClothingCard(
                    clothing = clothing,
                    onClick = { stepOption(1) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                RecommendationSlotCard(
                    suggestion = layer.generalSuggestion,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (optionCount > 1) {
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { stepOption(-1) }, modifier = Modifier.size(32.dp)) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.CHEVRON_LEFT,
                        contentDescription = "Previous ${layer.label}",
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(optionCount.coerceAtMost(5)) { index ->
                        val isActive = index == pagerState.currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isActive) 12.dp else 5.dp,
                            label = "slot_dot_width",
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                            label = "slot_dot_color",
                        )
                        Box(
                            Modifier
                                .width(dotWidth)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
                IconButton(onClick = { stepOption(1) }, modifier = Modifier.size(32.dp)) {
                    MaterialSymbol(
                        iconName = MaterialSymbols.CHEVRON_RIGHT,
                        contentDescription = "Next ${layer.label}",
                    )
                }
            }
        }
    }
}
