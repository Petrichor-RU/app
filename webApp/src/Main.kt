@file:OptIn(ExperimentalComposeUiApi::class)

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window
import nl.petrichor.app.App
import nl.petrichor.app.AppMode

/**
 * Web entry point. Serves the normal app, or the presentation deck when the page URL contains
 * `?mode=presentation` (writeups/2026-11-15-PRESENTATION.md). The storage actuals read the same
 * URL parameter themselves and switch to presentation-suffixed storage names.
 */
fun main() {
    val mode = AppMode.fromUrlQuery(window.location.search)
    ComposeViewport {
        App(mode = mode)
    }
}
