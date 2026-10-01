package nl.petrichor.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
private const val pagerPageCount = 1001

@Composable
fun App() {
    val initialPage = remember {
        val middlePage = pagerPageCount / 2
        middlePage - (middlePage % tabCount) + 1
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pagerPageCount })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage.mod(tabCount)

    MaterialSymbolsRenderingScope {
        MaterialTheme {
            Scaffold(
                containerColor = Color(0xFFF6F8FC),
                topBar = {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        NavigationBarItem(selected = selectedTab == 0, onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.pageForTab(0)) }
                        }, icon = {
                            MaterialSymbol(
                                iconName = MaterialSymbols.HOME,
                                contentDescription = null,
                            )
                        }, label = { Text("One") })
                        NavigationBarItem(selected = selectedTab == 1, onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.pageForTab(1)) }
                        }, icon = {
                            MaterialSymbol(
                                iconName = MaterialSymbols.CLOUD,
                                contentDescription = null,
                            )
                        }, label = { Text("Weather") })
                        NavigationBarItem(selected = selectedTab == 2, onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.pageForTab(2)) }
                        }, icon = {
                            MaterialSymbol(
                                iconName = MaterialSymbols.SETTINGS,
                                contentDescription = null,
                            )
                        }, label = { Text("Settings") })
                    }
                },
            ) { padding ->
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) { page ->
                    when (page.mod(tabCount)) {
                        2 -> WeatherScreen()
                        else -> BoxMessage("This tab is coming soon.")
                    }
                }
            }
        }
    }

}

private fun androidx.compose.foundation.pager.PagerState.pageForTab(tab: Int): Int {
    val currentTab = currentPage.mod(tabCount)
    val forwardDistance = (tab - currentTab + tabCount) % tabCount
    val backwardDistance = forwardDistance - tabCount
    val distance = if (forwardDistance <= -backwardDistance) forwardDistance else backwardDistance
    return (currentPage + distance).coerceIn(0, pagerPageCount - 1)
}
