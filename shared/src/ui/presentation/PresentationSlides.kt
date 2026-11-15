package nl.petrichor.app.ui.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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

/** Slide content column width cap (plan section 4.3), tunable. */
private val CONTENT_COLUMN_MAX_WIDTH = 720.dp

private val SLIDE_EDGE_PADDING = 24.dp

/**
 * The real slide list (plan section 4): title, overview, the four live tab screens and five
 * placeholder slides. The embedded tab screens create their own ViewModels here, exactly as in
 * the app's tab shell, and run against the presentation-scoped empty data.
 */
internal fun presentationSlides(weatherState: AppWeatherState): List<@Composable () -> Unit> =
    buildList {
        add { TitleSlide() }
        add { OverviewSlide() }
        add { TabSlide(caption = "Wardrobe") { WardrobeScreen() } }
        add { TabSlide(caption = "Journal") { JournalScreen() } }
        add { TabSlide(caption = "Suggestions") { RecommendationScreen(weatherState = weatherState) } }
        add { TabSlide(caption = "Weather") { WeatherScreen(weatherState = weatherState) } }
        repeat(PLACEHOLDER_SLIDE_COUNT) { number -> add { PlaceholderSlide(number = number + 1) } }
    }

/** Placeholder slides 7-11 of the deck (plan section 4.4); the footer counts within this run. */
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
 * Slide 2: short overview of what the app does. Draft copy from plan section 4.2, owner-editable.
 */
@Composable
internal fun OverviewSlide() {
    SlideContentColumn(verticalArrangement = Arrangement.spacedBy(16.dp), centerVertically = true) {
        Text("What is Petrichor?", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Petrichor helps you decide what to wear.",
            style = MaterialTheme.typography.titleMedium,
        )
        OverviewBullet("Your wardrobe, always with you")
        OverviewBullet("Log what you wore and how it felt")
        OverviewBullet("Personal comfort model, trained on you")
        OverviewBullet("Weather-aware outfit suggestions, every day")
    }
}

@Composable
private fun OverviewBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "\u2022", style = MaterialTheme.typography.bodyLarge)
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Slides 3-6: a live tab screen with a caption chip naming the tab. The screen is centred in a
 * column capped at [CONTENT_COLUMN_MAX_WIDTH] so large windows show the tab at a sensible size
 * instead of a stretched grid; the background park scene shows around it.
 */
@Composable
internal fun TabSlide(
    caption: String,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = CONTENT_COLUMN_MAX_WIDTH)
                .fillMaxWidth(),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start).padding(top = 16.dp, start = SLIDE_EDGE_PADDING),
            ) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
            }
        }
    }
}

/**
 * Slides 7-11: clearly placeholder content with no feature meaning. [number] is 1-based within
 * the placeholder run and drives the title and the footer.
 */
@Composable
internal fun PlaceholderSlide(number: Int) {
    SlideContentColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Placeholder title $number", style = MaterialTheme.typography.headlineMedium)
        Text(
            "This is example text. Replace it with your own content before presenting. It shows how a " +
                "regular content slide of this deck looks on the big screen.",
            style = MaterialTheme.typography.bodyLarge,
        )
        PlaceholderBullet("First example point")
        PlaceholderBullet("Second example point")
        PlaceholderBullet("Third example point")
        PlaceholderBullet("Fourth example point")
        PlaceholderBullet("Fifth example point")
        Spacer(Modifier.weight(1f))
        Text(
            text = "Slide $number of $PLACEHOLDER_SLIDE_COUNT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PlaceholderBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "\u2022", style = MaterialTheme.typography.bodyLarge)
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Shared slide layout: full-height column, capped width, safe padding. */
@Composable
private fun SlideContentColumn(
    verticalArrangement: Arrangement.Vertical,
    centerVertically: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = CONTENT_COLUMN_MAX_WIDTH)
                .safeDrawingPadding()
                .padding(horizontal = SLIDE_EDGE_PADDING, vertical = 48.dp),
            verticalArrangement =
                if (centerVertically) Arrangement.spacedBy(16.dp, Alignment.CenterVertically) else verticalArrangement,
            content = content,
        )
    }
}
