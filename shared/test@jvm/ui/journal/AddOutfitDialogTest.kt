package nl.petrichor.app.ui.journal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AddOutfitDialogTest {
    private val today = LocalDate(2026, 6, 10)
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

    private fun DesktopComposeUiTest.setDialog(onSave: suspend (LocalDate, String?, String?, List<ClothingPiece>) -> Boolean) {
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    AddOutfitDialog(
                        params =
                            AddOutfitDialogParams(
                                outfit = null,
                                clothingTypes = listOf(teeType, jeansType),
                                wardrobePieces = listOf(piece(1, "White tee", 10), piece(2, "Blue jeans", 12)),
                                today = today,
                                onDismiss = {},
                                onSave = onSave,
                            ),
                    )
                }
            }
        }
    }

    @Test
    fun `pieces are a free multi-select and save passes the selection through`() =
        runDesktopComposeUiTest(width = 1024, height = 1600) {
            var savedDate: LocalDate? = null
            var savedPieces: List<ClothingPiece>? = null
            setDialog { date, _, _, pieces ->
                savedDate = date
                savedPieces = pieces
                true
            }

            onNodeWithText("White tee").performClick()
            onNodeWithText("Blue jeans").performClick()
            // Title and confirm button both read "Log Outfit"; the button is the second node.
            val logOutfitNodes = onAllNodesWithText("Log Outfit").fetchSemanticsNodes()
            assertEquals(2, logOutfitNodes.size)
            onAllNodesWithText("Log Outfit")[1].performClick()
            waitForIdle()

            assertEquals(today, savedDate)
            assertEquals(listOf(1L, 2L), savedPieces?.map { it.id }?.sorted())
        }

    @Test
    fun `date stepper moves the saved date within bounds`() =
        runDesktopComposeUiTest(width = 1024, height = 1600) {
            var savedDate: LocalDate? = null
            setDialog { date, _, _, _ ->
                savedDate = date
                true
            }

            onNodeWithContentDescription("Next day").performClick()
            onAllNodesWithText("Log Outfit")[1].performClick()
            waitForIdle()

            assertEquals(LocalDate(2026, 6, 11), savedDate)
        }
}
