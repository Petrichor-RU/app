package nl.petrichor.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.coroutines.launch
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.ui.journal.JournalActionBus
import nl.petrichor.app.ui.journal.JournalPendingAction
import nl.petrichor.app.ui.journal.JournalScreen
import nl.petrichor.app.ui.recommendation.RecommendationScreen
import nl.petrichor.app.ui.wardrobe.WardrobeScreen
import nl.petrichor.app.ui.weather.WeatherScreen

private const val TAB_COUNT = 4
private const val JOURNAL_TAB = 1
private const val SUGGESTIONS_TAB = 2

private data class TabSpec(
    val label: String,
    val icon: String,
)

private val tabs =
    listOf(
        TabSpec(label = "Wardrobe", icon = MaterialSymbols.CHECKROOM),
        TabSpec(label = "Journal", icon = MaterialSymbols.BOOK),
        TabSpec(label = "Suggestions", icon = MaterialSymbols.FOR_YOU),
        TabSpec(label = "Weather", icon = MaterialSymbols.CLOUD),
    )

@Composable
fun App() {
    // The app opens on the Suggestions tab.
    val pagerState = rememberPagerState(initialPage = SUGGESTIONS_TAB, pageCount = { TAB_COUNT })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage
    val pendingJournalAction by JournalActionBus.action.collectAsStateWithLifecycle()

    // Retrain the recommendation model lazily at app start when the stored model is stale.
    LaunchedEffect(Unit) {
        OutfitLogRepository().ensureModelFresh()
    }

    // Notification deep links: animate to the Journal tab when an action is pending.
    LaunchedEffect(pendingJournalAction) {
        if (pendingJournalAction != null) {
            pagerState.animateScrollToPage(JOURNAL_TAB)
        }
    }

    MaterialSymbolsRenderingScope {
        MaterialTheme {
            Scaffold(
                containerColor = Color(0xFFF6F8FC),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                icon = {
                                    MaterialSymbol(
                                        iconName = tab.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                HorizontalPager(
                    state = pagerState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                ) { page ->
                    AppTabContent(
                        page = page,
                        pendingJournalAction = pendingJournalAction,
                        onActionConsumed = { JournalActionBus.consume() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTabContent(
    page: Int,
    pendingJournalAction: JournalPendingAction?,
    onActionConsumed: () -> Unit,
) {
    when (page) {
        0 -> {
            WardrobeScreen()
        }

        1 -> {
            JournalScreen(
                pendingAction = pendingJournalAction,
                onActionConsumed = onActionConsumed,
            )
        }

        2 -> {
            RecommendationScreen(modifier = Modifier.statusBarsPadding())
        }

        3 -> {
            WeatherScreen(modifier = Modifier.statusBarsPadding())
        }
    }
}
