package nl.petrichor.app.ui.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.petrichor.app.ui.background.AppWeatherState
import nl.petrichor.app.ui.journal.JournalScreen
import nl.petrichor.app.ui.recommendation.RecommendationScreen
import nl.petrichor.app.ui.wardrobe.WardrobeScreen
import nl.petrichor.app.ui.weather.WeatherScreen

/** Slide content panel width cap, tunable. */
private val CONTENT_PANEL_MAX_WIDTH = 720.dp

/** Tab demo slide content column width cap: roughly a phone screen, tunable. */
private val TAB_COLUMN_MAX_WIDTH = 720.dp

/** Slide content panel corner radius, tunable. */
private val CONTENT_PANEL_SHAPE = RoundedCornerShape(24.dp)

/** Alpha of the semi-transparent panel behind slide content, tunable. */
private const val CONTENT_PANEL_ALPHA = 0.72f

/**
 * The real slide list (writeups/2026-11-15-PRESENTATION.md, section 4): title, overview, the
 * four live tab screens and five placeholder slides. The embedded tab screens create their own
 * ViewModels here, exactly as in the app's tab shell, and run against the presentation-scoped
 * empty data.
 */
internal fun presentationSlides(weatherState: AppWeatherState): List<@Composable () -> Unit> =
    buildList {
        add { TitleSlide() }
        add { OverviewSlide() }
        add { TabSlide { WardrobeScreen() } }
        add { TabSlide { JournalScreen() } }
        add { TabSlide { RecommendationScreen(weatherState = weatherState) } }
        add { TabSlide { WeatherScreen(weatherState = weatherState) } }
        add { ProgressSlide() }
        add { GroupSlide() }
        add { RisksSlide() }
        add { ToolsSlide() }
    }

/** Placeholder slides 7-11 of the deck; their titles number them within this run. */
internal const val PLACEHOLDER_SLIDE_COUNT = 5

/**
 * Slide 1: the "Petrichor" title, nothing else. Display typography scaled to a fraction of the
 * shorter window dimension so it reads from the back of the room at any window size.
 */
@Composable
internal fun TitleSlide() {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shortSide = minOf(maxWidth, maxHeight)
        val fontSize = with(LocalDensity.current) { (shortSide * 0.18f).toSp() }
        Text(
            text = "Petrichor",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize, fontWeight = FontWeight.Bold),
        )
    }
}

/**
 * Slide 2: the app description, presented like any other content slide: a centered
 * semi-transparent panel with the title, the one-line pitch and the feature bullets.
 */
@Composable
internal fun OverviewSlide() {
    SlideContentPanel(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("What is Petrichor?", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Petrichor helps you decide what to wear.",
            style = MaterialTheme.typography.titleMedium,
        )
        SlideBullet("Wardrobe management")
        SlideBullet("Outfit logging: was it comfortable, was it too warm or cold?")
        SlideBullet("Comfort model trained on the outfits you logged")
        SlideBullet("Weather-aware outfit suggestions")
    }
}

@Composable
private fun SlideBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "\u2022", style = MaterialTheme.typography.bodyLarge)
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Slides 3-6: a live tab screen with no box behind it — the deck-wide scrim sits between it and
 * the park scene. The screen is centred in a column capped at [TAB_COLUMN_MAX_WIDTH] (roughly a
 * phone width) so large windows show the tab at a sensible size instead of a stretched grid.
 */
@Composable
internal fun TabSlide(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .fillMaxHeight()
                .widthIn(max = TAB_COLUMN_MAX_WIDTH)
                .fillMaxWidth(),
        ) {
            content()
        }
    }
}

/**
 * Slides 7-11: clearly placeholder content with no feature meaning. [number] is 1-based within
 * the placeholder run and drives the title.
 */
@Composable
internal fun PlaceholderSlide(number: Int) {
    SlideContentPanel(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Placeholder title $number", style = MaterialTheme.typography.headlineMedium)
        Text(
            "This is example text. Replace it with your own content before presenting. It shows how a " +
                "regular content slide of this deck looks on the big screen.",
            style = MaterialTheme.typography.bodyLarge,
        )
        SlideBullet("First example point")
        SlideBullet("Second example point")
        SlideBullet("Third example point")
        SlideBullet("Fourth example point")
        SlideBullet("Fifth example point")
    }
}

@Composable
internal fun ProgressSlide() {
    SlideContentPanel(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Progress", style = MaterialTheme.typography.headlineMedium)
        SlideBullet("We have a rough MVP going")
//        SlideBullet("Add integration with online clothing stores")
        SlideBullet("Pivot towards x")
        SlideBullet("Fourth example point")
        SlideBullet("Fifth example point")
    }
}

@Composable
internal fun RisksSlide() {
    SlideContentPanel(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Risks", style = MaterialTheme.typography.headlineMedium)
        SlideBullet("Lack of users")
        SlideBullet("Model needs data")
    }
}

@Composable
internal fun GroupSlide() {
    SlideContentPanel(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Group", style = MaterialTheme.typography.headlineMedium)
        SlideBullet("Two people on outreach and administration, three on development")
        SlideBullet("Every Tuesday, collaborate in a room")
    }
}
@Composable
internal fun ToolsSlide() {
    SlideContentPanel(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tools", style = MaterialTheme.typography.headlineMedium)
        SlideBullet("GitHub for the repository, Jira for tickets")
        SlideBullet("Obsidian for notekeeping")
        SlideBullet("Each developer has a different way of working")
        SlideBullet("Mistral Vibe for writing implementation plans, Vibe Code for implementation")
        SlideBullet("ChatGPT for help with implementation")
    }
}


/**
 * Shared slide layout for text content: the content column sits inside a semi-transparent panel,
 * centred on the slide. The deck-wide scrim sits behind this panel.
 */
@Composable
private fun SlideContentPanel(
    verticalArrangement: Arrangement.Vertical,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CONTENT_PANEL_SHAPE,
            color = MaterialTheme.colorScheme.surface.copy(alpha = CONTENT_PANEL_ALPHA),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = CONTENT_PANEL_MAX_WIDTH),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
    }
}
