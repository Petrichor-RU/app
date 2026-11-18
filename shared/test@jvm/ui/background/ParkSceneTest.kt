package nl.petrichor.app.ui.background

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.petrichor.app.data.background.BackgroundCondition
import nl.petrichor.app.data.background.TimeBand
import nl.petrichor.app.data.background.WeatherMood
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ParkSceneTest {
    @Test
    fun `all conditions render for visual review`() =
        runDesktopComposeUiTest(width = 1260, height = 1440) {
            setContent {
                Column {
                    for (band in TimeBand.entries) {
                        Row(Modifier.weight(1f)) {
                            for (mood in WeatherMood.entries) {
                                val condition = BackgroundCondition(band, mood)
                                MaterialTheme(colorScheme = if (condition.isDark()) parkDarkScheme else parkLightScheme) {
                                    Box(Modifier.weight(1f).fillMaxSize()) {
                                        ParkScene(condition, timeSeconds = { 8f })
                                        Text("$band\n$mood", color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            val directory = File("build/background-review").apply { mkdirs() }
            withContext(Dispatchers.IO) {
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "all-conditions.png"))
            }
        }

    @Test
    fun `scene allows content interaction at phone tablet and desktop sizes`() {
        for ((width, height) in listOf(360 to 800, 768 to 1024, 1024 to 768, 1440 to 900)) {
            runDesktopComposeUiTest(width = width, height = height) {
                var clicked = false
                setContent {
                    MaterialTheme(colorScheme = parkDarkScheme) {
                        Box(Modifier.fillMaxSize()) {
                            ParkScene(BackgroundCondition(TimeBand.NIGHT, WeatherMood.SNOW), timeSeconds = { 8f })
                            Button(onClick = { clicked = true }) { Text("Open wardrobe") }
                        }
                    }
                }
                onNodeWithText("Open wardrobe").performClick()
                assertTrue(clicked)
                val directory = File("build/background-review").apply { mkdirs() }
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(directory, "night-snow-$width.png"))
            }
        }
    }
}
