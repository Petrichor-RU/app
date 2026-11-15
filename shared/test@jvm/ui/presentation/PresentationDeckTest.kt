package nl.petrichor.app.ui.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlin.test.Test

private fun testSlides(): List<@Composable () -> Unit> =
    listOf(
        { Box(Modifier.fillMaxSize()) { Text("Slide one") } },
        { Box(Modifier.fillMaxSize()) { Text("Slide two") } },
        { Box(Modifier.fillMaxSize()) { Text("Slide three") } },
    )

class PresentationDeckTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `deck starts on the first slide with backward disabled`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        PresentationDeck(slides = testSlides())
                    }
                }
            }

            onNodeWithText("Slide one").assertIsDisplayed()
            onNodeWithText("1 / 3").assertIsDisplayed()
            onNodeWithContentDescription("Previous slide").assertIsNotEnabled()
            onNodeWithContentDescription("Next slide").assertIsEnabled()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `next button advances the deck forward`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        PresentationDeck(slides = testSlides())
                    }
                }
            }

            onNodeWithContentDescription("Next slide").performClick()
            onNodeWithText("Slide two").assertIsDisplayed()
            onNodeWithText("2 / 3").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `previous button retreats the deck backward`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        PresentationDeck(slides = testSlides())
                    }
                }
            }

            onNodeWithContentDescription("Next slide").performClick()
            onNodeWithText("Slide two").assertIsDisplayed()
            onNodeWithContentDescription("Previous slide").performClick()
            onNodeWithText("Slide one").assertIsDisplayed()
            onNodeWithText("1 / 3").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `next button is disabled on the last slide`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        PresentationDeck(slides = testSlides())
                    }
                }
            }

            onNodeWithContentDescription("Next slide").performClick()
            onNodeWithContentDescription("Next slide").performClick()
            onNodeWithText("Slide three").assertIsDisplayed()
            onNodeWithContentDescription("Next slide").assertIsNotEnabled()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `right arrow advances and left arrow retreats the deck`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        PresentationDeck(slides = testSlides())
                    }
                }
            }

            onRoot().performKeyInput {
                pressKey(Key.DirectionRight)
            }
            onNodeWithText("Slide two").assertIsDisplayed()

            onRoot().performKeyInput {
                pressKey(Key.DirectionLeft)
            }
            onNodeWithText("Slide one").assertIsDisplayed()
            onNodeWithText("1 / 3").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `arrow keys do nothing on the deck ends`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        PresentationDeck(slides = testSlides())
                    }
                }
            }

            onRoot().performKeyInput {
                pressKey(Key.DirectionLeft)
            }
            onNodeWithText("Slide one").assertIsDisplayed()
        }
}
