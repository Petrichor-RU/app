package nl.petrichor.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.petrichor.app.data.background.BackgroundConditionResolver
import nl.petrichor.app.data.outfit.OutfitLogRepository
import nl.petrichor.app.ui.background.AppBackgroundController
import nl.petrichor.app.ui.background.AppWeatherState
import nl.petrichor.app.ui.background.ParkBackground
import nl.petrichor.app.ui.background.SCENE_FADE_MILLIS
import nl.petrichor.app.ui.background.animatedParkColorScheme
import nl.petrichor.app.ui.background.isDark
import nl.petrichor.app.ui.background.tabScrimAlphas
import nl.petrichor.app.ui.journal.JournalActionBus
import nl.petrichor.app.ui.journal.JournalPendingAction
import nl.petrichor.app.ui.journal.JournalScreen
import nl.petrichor.app.ui.recommendation.RecommendationScreen
import nl.petrichor.app.ui.wardrobe.WardrobeScreen
import nl.petrichor.app.ui.weather.WeatherScreen
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

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

/**
 * App shell: owns the park background and everything shared across tabs. Layers, back to front:
 * the [ParkBackground] scene, a per-tab scrim ([tabScrimAlphas]) that follows the active scheme,
 * then a transparent [Scaffold] with the tab pager. Also owns the single weather load
 * ([AppBackgroundController]) shared with the Weather and Suggestions tabs, the sky-following
 * animated [MaterialTheme], and notification deep links into the Journal tab.
 *
 * @param onDarkThemeChanged called whenever the effective dark scheme changes; Android uses it
 *   to switch system-bar icon contrast at the crossfade midpoint, other platforms ignore it.
 */
@Composable
@Suppress("LongMethod") // App shell composes navigation and its shared scene/theme state.
fun App(onDarkThemeChanged: (Boolean) -> Unit = {}) {
    // The app opens on the Suggestions tab.
    val pagerState = rememberPagerState(initialPage = SUGGESTIONS_TAB, pageCount = { TAB_COUNT })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage
    val pendingJournalAction by JournalActionBus.action.collectAsStateWithLifecycle()

    val inspection = LocalInspectionMode.current
    val controller = if (inspection) null else viewModel { AppBackgroundController() }
    val weatherState = controller?.weather?.collectAsStateWithLifecycle()?.value ?: AppWeatherState()
    val condition =
        controller?.condition?.collectAsStateWithLifecycle()?.value
            ?: remember { BackgroundConditionResolver.resolve(null, Clock.System.now()) }
    val colorScheme = animatedParkColorScheme(condition.isDark())
    // Android system-bar icons still switch at the scene transition midpoint.
    var dark by remember { mutableStateOf(condition.isDark()) }
    LaunchedEffect(condition) {
        delay((SCENE_FADE_MILLIS / 2L).milliseconds)
        dark = condition.isDark()
    }
    LaunchedEffect(dark) { onDarkThemeChanged(dark) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(controller, lifecycle) {
        if (controller != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { controller.run() }
    }
    val scrimAlpha by animateFloatAsState(tabScrimAlphas[selectedTab], label = "Tab scene visibility")

    // Retrain the recommendation model lazily at app start when the stored model is stale.
    LaunchedEffect(Unit) {
        if (!inspection) OutfitLogRepository().ensureModelFresh()
    }

    // Notification deep links: animate to the Journal tab when an action is pending.
    LaunchedEffect(pendingJournalAction) {
        if (pendingJournalAction != null) {
            pagerState.animateScrollToPage(JOURNAL_TAB)
        }
    }

    MaterialSymbolsRenderingScope {
        MaterialTheme(colorScheme = colorScheme) {
            Box(Modifier.fillMaxSize()) {
                ParkBackground(condition)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = scrimAlpha)))
                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
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
                            weatherState = weatherState,
                            pendingJournalAction = pendingJournalAction,
                            onActionConsumed = { JournalActionBus.consume() },
                        )
                    }
                }
            }
        }
    }
}

/** Hosts the tab for [page], passing the shell-shared [weatherState] to the tabs that consume it. */
@Composable
private fun AppTabContent(
    page: Int,
    weatherState: AppWeatherState,
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
            RecommendationScreen(weatherState = weatherState, modifier = Modifier.statusBarsPadding())
        }

        3 -> {
            WeatherScreen(weatherState = weatherState, modifier = Modifier.statusBarsPadding())
        }
    }
}
