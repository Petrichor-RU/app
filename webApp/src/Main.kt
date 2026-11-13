@file:OptIn(ExperimentalComposeUiApi::class)

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import nl.petrichor.app.App

fun main() {
    ComposeViewport {
        App()
    }
}
