package nl.petrichor.app

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.net.URL
import javax.imageio.ImageIO

/**
 * Desktop entry point. Launches as the normal app, or as the presentation deck with
 * `--presentation` (or `-Dpetrichor.mode=presentation`): see writeups/2026-11-15-PRESENTATION.md.
 * The parsed mode is announced through the `petrichor.mode` system property before any storage is
 * created, so the data actuals can move to the isolated `~/.petrichor-presentation` root.
 */
fun main(args: Array<String>) {
    val mode = AppMode.fromJvmLaunchArgs(args.toList(), System.getProperty(AppMode.JVM_MODE_PROPERTY))
    if (mode == AppMode.PRESENTATION) {
        System.setProperty(AppMode.JVM_MODE_PROPERTY, AppMode.PRESENTATION_VALUE)
    }
    application {
        val icon =
            remember {
                val url: URL =
                    requireNotNull(Thread.currentThread().contextClassLoader.getResource("app-logo.png")) {
                        "app-logo.png missing from desktopApp resources"
                    }
                ImageIO.read(url).toPainter()
            }
        Window(onCloseRequest = ::exitApplication, title = "petrichor", icon = icon) {
            App(mode = mode)
        }
    }
}
