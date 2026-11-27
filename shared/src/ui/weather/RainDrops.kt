package nl.petrichor.app.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import kotlin.math.roundToInt

private const val RAIN_MIN_MM = 10.0
private const val RAIN_MM_PER_DROP = 50.0
private const val MAX_RAIN_DROPS = 5

/**
 * Rain indicator: one water drop per [RAIN_MM_PER_DROP] mm, shown from [RAIN_MIN_MM] mm on and
 * capped at [MAX_RAIN_DROPS]. The fixed-height row keeps headers aligned when a period is dry;
 * the row carries a content description with the mm value so the graphic is not purely decorative.
 */
@Composable
internal fun RainDrops(
    precipitationMm: Double,
    modifier: Modifier = Modifier,
) {
    val drops =
        if (precipitationMm < RAIN_MIN_MM) {
            0
        } else {
            (precipitationMm / RAIN_MM_PER_DROP).roundToInt().coerceIn(1, MAX_RAIN_DROPS)
        }
    Row(
        modifier =
            modifier
                .height(12.dp)
                .semantics {
                    if (drops > 0) contentDescription = "Rain, ${precipitationMm.roundToInt()} mm"
                },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        repeat(drops) {
            MaterialSymbol(
                iconName = MaterialSymbols.WATER_DROP,
                contentDescription = null,
                size = 10.dp,
                // Follow the surrounding text colour: dark on light cards, white on dark cards.
                tint = LocalContentColor.current,
            )
        }
    }
}
