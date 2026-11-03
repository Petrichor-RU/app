package nl.petrichor.app.ui.weather

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols

@Composable
fun LocationHeader(
    locationName: String,
    pickerOpen: Boolean,
    onTogglePicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Location",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol(
                    iconName = MaterialSymbols.LOCATION_ON,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                AnimatedContent(
                    targetState = locationName,
                    transitionSpec = {
                        (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { height -> height / 2 } +
                            fadeIn(tween(220)))
                            .togetherWith(
                                slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { height -> -height / 2 } +
                                    fadeOut(tween(120))
                            )
                            .using(SizeTransform(clip = false))
                    },
                    label = "location_name",
                ) { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        FilledTonalIconButton(onClick = onTogglePicker) {
            AnimatedContent(
                targetState = pickerOpen,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f) +
                        fadeIn(tween(150)))
                        .togetherWith(scaleOut(tween(120), targetScale = 0.6f) + fadeOut(tween(120)))
                },
                label = "picker_toggle_icon",
            ) { open ->
                MaterialSymbol(
                    iconName = if (open) MaterialSymbols.CLOSE else MaterialSymbols.SEARCH,
                    contentDescription = if (open) "Close location picker" else "Search locations",
                )
            }
        }
    }
}
