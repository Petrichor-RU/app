package nl.petrichor.app.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import nl.petrichor.app.data.guide.AdviceKind
import nl.petrichor.app.data.guide.ClimateProfile
import nl.petrichor.app.data.guide.GuideEngine
import nl.petrichor.app.data.guide.Season
import kotlin.math.roundToInt

/**
 * The guide's advice cards: a "General" card first when some advice holds for all four seasons,
 * then one lightly season-tinted card per meteorological season (Winter → Autumn). These are the
 * only components in the guide with a background colour.
 */
@Composable
internal fun GuideSeasonCards(
    profile: ClimateProfile,
    modifier: Modifier = Modifier,
) {
    val general = GuideEngine.generalAdvice(profile)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (general.isNotEmpty()) {
            GuideGeneralCard(advice = general)
        }
        Season.all.forEach { season ->
            SeasonCard(profile = profile, season = season, excluded = general.toSet())
        }
    }
}

@Composable
private fun SeasonCard(
    profile: ClimateProfile,
    season: Season,
    excluded: Set<AdviceKind>,
) {
    val summary = GuideEngine.seasonSummary(profile, season)
    // Kinds already shown in the General section are not repeated on the season card.
    val advice = GuideEngine.seasonAdvice(profile, season).filterNot { it in excluded }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = seasonCardColor(season)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = season.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "• ${summary.meanHigh.roundToInt()}° / ${summary.meanLow.roundToInt()}°",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                RainDrops(summary.meanPrecipitationMm)
            }
            if (advice.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                advice.forEach { kind -> AdviceRow(kind) }
            }
        }
    }
}

/** One icon + sentence row per advice kind; everything shows at once, no second-level fold-outs. */
@Composable
internal fun AdviceRow(kind: AdviceKind) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        MaterialSymbol(
            iconName = kind.icon,
            contentDescription = null,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = kind.template, style = MaterialTheme.typography.bodyMedium)
    }
}
