package nl.petrichor.app.ui.weather

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        when {
            highlighted -> 1.02f
            pressed -> 0.98f
            else -> 1f
        }
    )
    val containerColor by animateColorAsState(
        if (highlighted) colors.primaryContainer else cardColors.containerColor
    )
    val contentColor by animateColorAsState(
        if (highlighted) colors.onPrimaryContainer else cardColors.contentColor
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
        shape = CardDefaults.shape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol(
                iconName = icon,
                contentDescription = null,
                tint = if (highlighted) colors.primary else iconTint,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.75f),
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
