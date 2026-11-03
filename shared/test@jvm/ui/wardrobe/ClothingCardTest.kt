package nl.petrichor.app.ui.wardrobe

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.wardrobe.ClothingPiece
import kotlin.test.Test

class ClothingCardTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `clothing card displays item image semantics and name`() = runDesktopComposeUiTest {
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    ClothingCard(
                        clothing = ClothingPiece(
                            name = "Blue jacket",
                            typeId = 1L,
                            notes = null,
                            imagePath = "/tmp/wardrobe-images/blue-jacket.jpg",
                            thumbnailPath = "/tmp/wardrobe-images/blue-jacket.jpg"
                        ),
                        onClick = {}
                    )
                }
            }
        }

        onNodeWithContentDescription("Blue jacket").assertIsDisplayed()
        onNodeWithText("Blue jacket").assertIsDisplayed()
    }
}
