@file:Suppress("MatchingDeclarationName") // platform actual of the common expect object

package nl.petrichor.app.platform

import java.awt.Desktop
import java.net.URI

actual object PlatformBrowser {
    @Suppress("TooGenericExceptionCaught") // headless/unsupported desktops must not crash the app
    actual fun open(url: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            }
        } catch (_: Exception) {
            // No system browser available; the link stays in the detail screen.
        }
    }
}
