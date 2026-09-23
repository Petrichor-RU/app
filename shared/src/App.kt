package nl.petrichor.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.WeatherScreen

@Composable
fun App() {
    var selectedTab by remember { mutableStateOf(1) }

    MaterialTheme {
        Scaffold(
            containerColor = Color(0xFFF6F8FC),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Text("1") }, label = { Text("One") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Text("W") }, label = { Text("Weather") })
                    NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Text("3") }, label = { Text("Three") })
                }
            },
        ) { padding ->
            if (selectedTab != 1) {
                BoxMessage("This tab is coming soon.", Modifier.padding(padding))
            } else {
                WeatherScreen(modifier = Modifier.padding(padding))
            }
        }
    }
}
