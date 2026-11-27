package nl.petrichor.app.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.petrichor.app.data.guide.MonthClimate
import kotlin.math.roundToInt

// The strip is a self-contained chart panel that follows the theme: the scheme's surface colour
// as the panel background and its default text colour for the labels, in both light and dark.

// Bar colour is mapped by absolute temperature: blue at -30 °C, red at 45 °C, at a faint alpha.
private val COLOUR_COLD = Color(0xFF1E88E5)
private val COLOUR_HOT = Color(0xFFE53935)
private const val COLOUR_MIN_C = -30.0
private const val COLOUR_MAX_C = 45.0
private const val BAR_ALPHA = 0.35f

private val BAR_TRACK_HEIGHT = 72.dp
private val BAR_CORNER_RADIUS = 4.dp

/** Bar colour for [temperatureC] on the fixed −30…45 °C colour scale, faint enough to stay subtle on white. */
private fun temperatureColor(temperatureC: Double): Color {
    val fraction = ((temperatureC - COLOUR_MIN_C) / (COLOUR_MAX_C - COLOUR_MIN_C)).toFloat().coerceIn(0f, 1f)
    return lerp(COLOUR_COLD, COLOUR_HOT, fraction).copy(alpha = BAR_ALPHA)
}

/**
 * Compacted bar graph of the 12 monthly normals on a chart panel that follows the theme (surface
 * background, default text colour for the labels). The track is scaled to the profile's own
 * extremes, shown by the legend on the left (max avgHigh on top, min avgLow at the bottom); each
 * month is a faint gradient range bar (blue at −30 °C, red at 45 °C). Instead of per-month
 * labels, "January" sits at the bottom left, "July" at the left of the seventh bar and
 * "December" at the bottom right. Rain is shown on the season cards instead.
 */
@Composable
internal fun GuideMonthlyStrip(
    months: List<MonthClimate>,
    modifier: Modifier = Modifier,
) {
    val scaleMin = months.minOfOrNull { it.avgLow } ?: COLOUR_MIN_C
    val scaleMax = months.maxOfOrNull { it.avgHigh } ?: COLOUR_MAX_C
    val scaleSpan = (scaleMax - scaleMin).coerceAtLeast(1.0)
    val panelColor = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .fillMaxWidth()
            .background(panelColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            ScaleLegend(scaleMin = scaleMin, scaleMax = scaleMax, labelColor = labelColor)
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    months.forEach { month ->
                        MonthBar(month, scaleMin = scaleMin, scaleSpan = scaleSpan, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                MonthLabels(monthCount = months.size, labelColor = labelColor)
            }
        }
    }
}

/**
 * Bottom label row mirroring the bars (same weighted cells and spacing): "January" at the left
 * of the first bar, "July" at the left of the seventh. "December" is overlaid on the full row
 * width, right-aligned, so it overflows to the LEFT into the empty cells rather than out of the
 * graph to the right. All labels are single-line and may overflow their narrow cell instead of
 * wrapping mid-word.
 */
@Composable
private fun MonthLabels(
    monthCount: Int,
    labelColor: Color,
) {
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(monthCount) { index ->
                Box(Modifier.weight(1f)) {
                    val label =
                        when (index) {
                            0 -> "January"
                            6 -> "July"
                            else -> null
                        }
                    if (label != null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = labelColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
            }
        }
        Text(
            text = "December",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.End,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(),
        )
    }
}

/** Scale legend on the left of the chart: the profile's max temperature on top, min at the bottom of the track. */
@Composable
private fun ScaleLegend(
    scaleMin: Double,
    scaleMax: Double,
    labelColor: Color,
) {
    Column(
        modifier = Modifier.height(BAR_TRACK_HEIGHT).padding(end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${scaleMax.roundToInt()}°", style = MaterialTheme.typography.labelSmall, color = labelColor)
        Text("${scaleMin.roundToInt()}°", style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}

@Composable
private fun MonthBar(
    month: MonthClimate,
    scaleMin: Double,
    scaleSpan: Double,
    modifier: Modifier = Modifier,
) {
    val lowFraction = ((month.avgLow - scaleMin) / scaleSpan).toFloat().coerceIn(0f, 1f)
    val highFraction = ((month.avgHigh - scaleMin) / scaleSpan).toFloat().coerceIn(0f, 1f)
    Box(modifier.height(BAR_TRACK_HEIGHT).fillMaxWidth()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(BAR_TRACK_HEIGHT * (1f - highFraction)))
            Box(
                Modifier
                    .height(BAR_TRACK_HEIGHT * (highFraction - lowFraction).coerceAtLeast(0.05f))
                    .fillMaxWidth(0.7f)
                    .align(Alignment.CenterHorizontally)
                    .background(
                        // Hot end (bar top) to cold end (bar bottom) on the absolute colour scale.
                        Brush.verticalGradient(listOf(temperatureColor(month.avgHigh), temperatureColor(month.avgLow))),
                        RoundedCornerShape(BAR_CORNER_RADIUS),
                    ),
            )
        }
    }
}
