package nl.petrichor.app.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.petrichor.app.data.weather.WeatherSnapshot

@Composable
fun WeatherContent(
    weather: WeatherSnapshot,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text(weather.description, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${weather.temperature}°", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text("Feels like ${weather.feelsLike}°", modifier = Modifier.padding(bottom = 10.dp))
                }
                Text("Humidity ${weather.humidity}%  |  Wind ${weather.windSpeed} km/h", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("5-day forecast", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(weather.forecast) { day ->
                Card(Modifier.width(108.dp)) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.label.substringAfter("-"), fontWeight = FontWeight.SemiBold)
                        Text("${day.max}°", style = MaterialTheme.typography.titleLarge)
                        Text("${day.min}°", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
