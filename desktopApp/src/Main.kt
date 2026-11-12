package nl.petrichor.app

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.net.URL
import javax.imageio.ImageIO

fun main() =
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
            App()
        }
    }
