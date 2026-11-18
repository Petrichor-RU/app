package nl.petrichor.app.ui.journal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PiecePickerOverlay is hosted in its own dialog window in production, which desktop ui tests
 * cannot inject input into — so it is tested directly here, the same way the wardrobe renders
 * its overlays in tests.
 */
@OptIn(ExperimentalTestApi::class)
class PiecePickerOverlayTest {
    private val teeType = ClothingType(id = 10, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)
    private val jeansType = ClothingType(id = 12, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)

    private fun piece(
        id: Long,
        name: String,
        typeId: Long,
    ) = ClothingPiece(
        id = id,
        name = name,
        typeId = typeId,
        material = Material.COTTON,
        notes = null,
        imagePath = "",
        thumbnailPath = "",
    )

    private fun DesktopComposeUiTest.setOverlay(
        selectedPieceIds: Set<Long> = emptySet(),
        onToggle: (Long) -> Unit = {},
        onDone: () -> Unit = {},
    ) {
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    PiecePickerOverlay(
                        pieces = listOf(piece(1, "White tee", 10), piece(2, "Blue jeans", 12)),
                        clothingTypes = listOf(teeType, jeansType),
                        selectedPieceIds = selectedPieceIds,
                        onToggle = onToggle,
                        onDone = onDone,
                    )
                }
            }
        }
    }

    @Test
    fun `tapping a card reports the toggle callback`() =
        runDesktopComposeUiTest(width = 800, height = 600) {
            var toggledId: Long? = null
            setOverlay(onToggle = { toggledId = it })

            onNodeWithText("White tee").performClick()

            assertEquals(1L, toggledId)
        }

    @Test
    fun `selection badge reflects the passed selection`() =
        runDesktopComposeUiTest(width = 800, height = 600) {
            setOverlay(selectedPieceIds = setOf(1L))

            onNodeWithText("1 selected").assertIsDisplayed()
            assertEquals(1, onAllNodesWithContentDescription("Selected").fetchSemanticsNodes().size)
            assertEquals(1, onAllNodesWithContentDescription("Not selected").fetchSemanticsNodes().size)
        }

    @Test
    fun `done reports onDone`() =
        runDesktopComposeUiTest(width = 800, height = 600) {
            var done = false
            setOverlay(onDone = { done = true })

            waitForIdle()
            onNodeWithContentDescription("Done").performClick()

            assertTrue(done)
        }

    @Test
    fun `type filter narrows the visible pieces`() =
        runDesktopComposeUiTest(width = 800, height = 600) {
            setOverlay()

            onNodeWithContentDescription("Filter").performClick()
            onNodeWithText("Jeans").performClick()
            waitForIdle()

            onNodeWithText("Blue jeans").assertIsDisplayed()
            assertEquals(0, onAllNodesWithText("White tee").fetchSemanticsNodes().size)
        }
}
