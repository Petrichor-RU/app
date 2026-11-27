package nl.petrichor.app.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.guide.ClimateProfile
import nl.petrichor.app.data.guide.Season

// Seasonal tint constants for the guide's season cards, in one place. Each tint is applied at low
// alpha over the same translucent surface base as the forecast cards (see seasonCardColor), so the
// cards stay legible on the animated park background in both light and dark schemes.
private val seasonTints: Map<Season, Color> =
    mapOf(
        Season.WINTER to Color(0xFF64B5F6), // faint cool blue
        Season.SPRING to Color(0xFF81C784), // faint green
        Season.SUMMER to Color(0xFFFFD54F), // faint warm amber
        Season.AUTUMN to Color(0xFFFF8A65), // faint orange
    )

private const val SEASON_TINT_ALPHA = 0.10f
private const val SEASON_CARD_BASE_ALPHA = 0.84f

/** Seasonal tint at low alpha composited over the forecast cards' translucent surface base. */
@Composable
internal fun seasonCardColor(season: Season): Color =
    seasonTints
        .getValue(season)
        .copy(alpha = SEASON_TINT_ALPHA)
        .compositeOver(MaterialTheme.colorScheme.surface.copy(alpha = SEASON_CARD_BASE_ALPHA))

/**
 * Expandable "Location guide" section below the 5-day forecast: a monthly climate strip plus one
 * advice card per season. The section is always visible — it has no visibility switches of its
 * own: the header renders even with no [profile] and while [loading] (a new place's climate is
 * being fetched) the chevron becomes a spinner, and the previous profile stays on screen until the
 * new one arrives — or, when that fetch fails, the profile is cleared and the not-available
 * message below shows instead. Only the expandable content is conditional on a profile. The guide card and its
 * header keep a transparent container in both collapsed and expanded states; only the season cards
 * carry a background. Icons come from the app-wide `MaterialSymbolsRenderingScope` in App.kt.
 */
@Composable
internal fun LocationGuide(
    profile: ClimateProfile?,
    loading: Boolean = false,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                draggedElevation = 0.dp,
                disabledElevation = 0.dp,
            ),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            GuideHeader(loading = loading, expanded = expanded, onToggleExpanded = onToggleExpanded)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val p = profile
                if (p != null) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        GuideMonthlyStrip(months = p.months)
                        Spacer(Modifier.height(16.dp))
                        GuideSeasonCards(profile = p)
                    }
                } else {
                    // The section is always visible, so say so when there is no data to show
                    // (e.g. the climate request failed) instead of rendering a blank area.
                    Text(
                        text = "No climate data available for this location yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Transparent header row; the whole row is the touch target that toggles expansion. While
 * [loading], the chevron is replaced by a spinner.
 */
@Composable
private fun GuideHeader(
    loading: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "Location guide expand arrow",
    )
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Location guide",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp).semantics { contentDescription = "Loading location guide" },
                strokeWidth = 3.dp,
            )
        } else {
            MaterialSymbol(
                iconName = MaterialSymbols.KEYBOARD_ARROW_DOWN,
                contentDescription = if (expanded) "Collapse location guide" else "Expand location guide",
                size = 28.dp,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}
