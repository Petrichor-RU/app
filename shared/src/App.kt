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

private const val tabCount = 3

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
                        NavigationBarItem(selected = selectedTab == 0, onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        }, icon = {
                            MaterialSymbol(
                                iconName = MaterialSymbols.HOME,
                                contentDescription = null,
                            )
                        }, label = { Text("One") })
                        NavigationBarItem(selected = selectedTab == 1, onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        }, icon = {
                            MaterialSymbol(
                                iconName = MaterialSymbols.SETTINGS,
                                contentDescription = null,
                            )
                        }, label = { Text("Settings") })
                        NavigationBarItem(selected = selectedTab == 2, onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
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
                        2 -> WeatherScreen()
                        else -> BoxMessage("This tab is coming soon.")
                    }
                }
            }
        }
    }

}
