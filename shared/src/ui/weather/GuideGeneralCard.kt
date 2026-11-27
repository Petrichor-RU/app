package nl.petrichor.app.ui.weather

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.petrichor.app.data.guide.AdviceKind

/**
 * The "General" section of the location guide: advice that holds for all four seasons, on a
 * neutral card (the same translucent surface base as the forecast cards, no seasonal tint).
 */
@Composable
internal fun GuideGeneralCard(
    advice: List<AdviceKind>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "General",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            advice.forEach { kind -> AdviceRow(kind) }
        }
    }
}
