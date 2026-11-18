package nl.petrichor.app.ui.background

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class AnimatedParkThemeTest {
    @Test
    fun `theme animates both ways and can reverse before reaching its target`() =
        runDesktopComposeUiTest {
            val dark = mutableStateOf(false)
            var displayed = parkLightScheme
            mainClock.autoAdvance = false
            setContent {
                val colors = animatedParkColorScheme(dark.value)
                SideEffect { displayed = colors }
                MaterialTheme(colorScheme = colors) {
                    Surface { Text("Journal entry") }
                }
            }
            runOnIdle { dark.value = true }
            mainClock.advanceTimeBy(800)
            runOnIdle {
                assertNotEquals(parkLightScheme.surfaceContainerHighest, displayed.surfaceContainerHighest)
                assertNotEquals(parkDarkScheme.surfaceContainerHighest, displayed.surfaceContainerHighest)
                assertNotEquals(parkLightScheme.onSurface, displayed.onSurface)
                assertNotEquals(parkDarkScheme.onSurface, displayed.onSurface)
                dark.value = false
            }
            mainClock.advanceTimeBy(SCENE_FADE_MILLIS + 100L)
            runOnIdle {
                assertEquals(parkLightScheme.surfaceContainerHighest, displayed.surfaceContainerHighest)
                dark.value = true
            }
            mainClock.advanceTimeBy(SCENE_FADE_MILLIS + 100L)
            runOnIdle {
                assertEquals(parkDarkScheme.primary, displayed.primary)
                assertEquals(parkDarkScheme.surfaceContainerHighest, displayed.surfaceContainerHighest)
                dark.value = false
            }
            mainClock.advanceTimeBy(800)
            runOnIdle {
                assertNotEquals(parkDarkScheme.surface, displayed.surface)
                assertNotEquals(parkLightScheme.surface, displayed.surface)
            }
            mainClock.advanceTimeBy(SCENE_FADE_MILLIS + 100L)
            runOnIdle { assertEquals(parkLightScheme.surface, displayed.surface) }
        }
}
