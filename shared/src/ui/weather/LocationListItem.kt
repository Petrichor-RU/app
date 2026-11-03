package nl.petrichor.app.ui.weather

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols

private val itemShape = RoundedCornerShape(12.dp)

private const val pressedScale = 0.97f
private const val highlightedScale = 1.015f

@Composable
fun LocationListItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String = MaterialSymbols.LOCATION_ON,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlighted: Boolean = false,
    trailingAction: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val cardColors = CardDefaults.cardColors()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            highlighted -> highlightedScale
            pressed -> pressedScale
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "location_item_scale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (highlighted) colors.primaryContainer else cardColors.containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "location_item_container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (highlighted) colors.onPrimaryContainer else cardColors.contentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "location_item_content",
    )
    val subtitleColor by animateColorAsState(
        targetValue = if (highlighted) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "location_item_subtitle",
    )
    val resolvedIconTint by animateColorAsState(
        targetValue = if (highlighted) colors.primary else iconTint,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "location_item_icon",
    )
    val elevation by animateDpAsState(
        targetValue = if (highlighted) 2.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "location_item_elevation",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .padding(vertical = 2.dp, horizontal = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth(),
        shape = itemShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = elevation,
        interactionSource = source,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol(iconName = icon, contentDescription = null, tint = resolvedIconTint)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailingAction != null) {
                trailingAction()
            }
        }
    }
}
