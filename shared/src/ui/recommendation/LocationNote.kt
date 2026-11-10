package nl.petrichor.app.ui.recommendation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import nl.petrichor.app.data.recommendation.DayWeather

@Composable
internal fun LocationNote(
    locationName: String,
    dayWeather: DayWeather,
) {
    val tempSuffix = if (dayWeather.maxTemperature != null && dayWeather.minTemperature != null) {
        " • ${dayWeather.maxTemperature}° / ${dayWeather.minTemperature}°"
    } else {
        ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbol(
            iconName = MaterialSymbols.LOCATION_ON,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Based on $locationName$tempSuffix",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
