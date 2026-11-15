package nl.petrichor.app.ui.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nl.petrichor.app.ui.background.AppWeatherState

/**
 * Presentation-mode root composable (writeups/2026-11-15-PRESENTATION.md): hosts the slide deck
 * with the real tab screens as default slides. Hosted by `App(mode = AppMode.PRESENTATION)` so
 * the shared park background, theme and scrim stay in App.kt; [slides] is replaceable so tests
 * can drive the deck with fake content.
 */
@Composable
internal fun PetrichorPresentation(
    weatherState: AppWeatherState,
    modifier: Modifier = Modifier,
    slides: List<@Composable () -> Unit> = presentationSlides(weatherState),
) {
    PresentationDeck(slides = slides, modifier = modifier)
}
