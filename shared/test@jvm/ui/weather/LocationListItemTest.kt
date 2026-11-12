package nl.petrichor.app.ui.weather

import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbol
import dev.catbit.material_symbols.MaterialSymbols
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationListItemTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `item shows its name and region`() = runDesktopComposeUiTest {
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    LocationListItem(title = "Utrecht", subtitle = "Utrecht, NL", onClick = {})
                }
            }
        }

        onNodeWithText("Utrecht", substring = false).assertIsDisplayed()
        onNodeWithText("Utrecht, NL").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `clicking the item reports a selection`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    LocationListItem(title = "Rotterdam", onClick = { clicks++ })
                }
            }
        }

        onNodeWithText("Rotterdam").performClick()

        assertEquals(1, clicks)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `trailing action is clickable without selecting the item`() = runDesktopComposeUiTest {
        var selected = 0
        var removed = 0
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    LocationListItem(
                        title = "Delft",
                        highlighted = true,
                        onClick = { selected++ },
                        trailingAction = {
                            IconButton(onClick = { removed++ }) {
                                MaterialSymbol(iconName = MaterialSymbols.CLOSE, contentDescription = "Remove Delft")
                            }
                        }
                    )
                }
            }
        }

        onNodeWithContentDescription("Remove Delft").performClick()

        assertEquals(1, removed)
        assertEquals(0, selected)
    }
}
