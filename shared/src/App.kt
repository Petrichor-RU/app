package nl.petrichor.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.coroutines.launch

import nl.petrichor.app.ui.BoxMessage
import nl.petrichor.app.ui.WeatherScreen
import nl.petrichor.app.ui.wardrobe.WardrobeScreen

private const val tabCount = 4

@Composable
fun App() {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabCount })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage

    MaterialSymbolsRenderingScope {
        MaterialTheme {
            Scaffold(
                containerColor = Color(0xFFF6F8FC),
                bottomBar = {
                    NavigationBar(
                    ) {
                        // Wardrobe tab at position 0
                        NavigationBarItem(selected = selectedTab == 0, onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        }, icon = {
                            MaterialSymbol(
                                iconName = MaterialSymbols.CHECKROOM,
                                contentDescription = null,
                            )
                        }, label = { Text("Wardrobe") })
                        NavigationBarItem(selected = selectedTab == 1, onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        }, icon = {
                            MaterialSymbol(
                                iconName = MaterialSymbols.HOME,
                                contentDescription = null,
                            )
                        }, label = { Text("Home") })
                        NavigationBarItem(selected = selectedTab == 2, onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        }, icon = {
                            MaterialSymbol(
                                iconName = MaterialSymbols.SETTINGS,
                                contentDescription = null,
                            )
                        }, label = { Text("Settings") })
                        NavigationBarItem(selected = selectedTab == 3, onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        }, icon = {
                            MaterialSymbol(
                                iconName = MaterialSymbols.CLOUD,
                                contentDescription = null,
                            )
                        }, label = { Text("Weather") })
                    }
                },
            ) { padding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) { page ->
                    when (page) {
                        0 -> WardrobeScreen()
                        3 -> WeatherScreen()
                        else -> BoxMessage("This tab is coming soon.")
                    }
                }
            }
        }
    }

}
