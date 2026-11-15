package nl.petrichor.app.ui.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlin.test.Test

class PresentationSlidesTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `title slide shows the app name`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        TitleSlide()
                    }
                }
            }

            onNodeWithText("Petrichor").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `overview slide shows the app overview copy`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        OverviewSlide()
                    }
                }
            }

            onNodeWithText("What is Petrichor?").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `placeholder slide shows title, bullets and has no footer`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        PlaceholderSlide(number = 3)
                    }
                }
            }

            onNodeWithText("Placeholder title 3").assertIsDisplayed()
            onNodeWithText("Third example point").assertIsDisplayed()
            onNodeWithText("Fifth example point").assertIsDisplayed()
            onNodeWithText("Slide 3 of $PLACEHOLDER_SLIDE_COUNT").assertDoesNotExist()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `tab slide renders the embedded content without a tab label`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        TabSlide {
                            Text("Fake tab content")
                        }
                    }
                }
            }

            onNodeWithText("Fake tab content").assertIsDisplayed()
            onNodeWithText("Wardrobe").assertDoesNotExist()
        }
}
