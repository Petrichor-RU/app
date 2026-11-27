@file:Suppress("MatchingDeclarationName") // platform actual of the common expect object

package nl.petrichor.app.platform

import kotlinx.browser.window

actual object PlatformBrowser {
    actual fun open(url: String) {
        window.open(url, "_blank")
    }
}
