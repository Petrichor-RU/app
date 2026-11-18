package nl.petrichor.app.ui.wardrobe

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.wardrobe.ClothingPiece
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClothingCardTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `clothing card displays item image semantics and name`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        ClothingCard(
                            clothing =
                                ClothingPiece(
                                    name = "Blue jacket",
                                    typeId = 1L,
                                    notes = null,
                                    imagePath = "/tmp/wardrobe-images/blue-jacket.jpg",
                                    thumbnailPath = "/tmp/wardrobe-images/blue-jacket.jpg",
                                ),
                            onClick = {},
                        )
                    }
                }
            }

            onNodeWithContentDescription("Blue jacket").assertIsDisplayed()
            onNodeWithText("Blue jacket").assertIsDisplayed()
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `laundry badge at the top right is shown when a laundry action is provided and invokes it`() =
        runDesktopComposeUiTest {
            var laundryClicked = false
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        ClothingCard(
                            clothing =
                                ClothingPiece(
                                    name = "Blue jacket",
                                    typeId = 1L,
                                    notes = null,
                                    imagePath = "",
                                    thumbnailPath = "",
                                ),
                            onClick = {},
                            onLaundryClick = { laundryClicked = true },
                        )
                    }
                }
            }

            onNodeWithContentDescription("Mark Blue jacket in laundry").assertIsDisplayed()
            onNodeWithContentDescription("Mark Blue jacket in laundry").performClick()
            assertTrue(laundryClicked)
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `no laundry badge is shown without a laundry action`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        ClothingCard(
                            clothing =
                                ClothingPiece(
                                    name = "Blue jacket",
                                    typeId = 1L,
                                    notes = null,
                                    imagePath = "",
                                    thumbnailPath = "",
                                ),
                            onClick = {},
                        )
                    }
                }
            }

            assertEquals(0, onAllNodesWithContentDescription("Mark Blue jacket in laundry").fetchSemanticsNodes().size)
        }
}
