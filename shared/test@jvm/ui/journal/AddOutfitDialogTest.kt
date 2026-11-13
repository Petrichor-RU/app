package nl.petrichor.app.ui.journal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.datetime.LocalDate
import nl.petrichor.app.data.outfit.OutfitEntry
import nl.petrichor.app.data.outfit.OutfitEntryWithPieces
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

    private fun existingOutfit(vararg pieces: ClothingPiece) =
        OutfitEntryWithPieces(
            OutfitEntry(
                id = 5,
                wornOnDate = today,
                photoPath = null,
                notes = null,
                temperatureC = null,
                feelsLikeC = null,
                minTempC = null,
                maxTempC = null,
                weatherDescription = null,
                weatherIcon = null,
                isRainy = false,
                surveyFeelingScore = null,
                surveyWarmthVerdict = null,
                surveyedAtEpochMs = null,
            ),
            pieces = pieces.toList(),
        )

    private fun DesktopComposeUiTest.setDialog(
        outfit: OutfitEntryWithPieces? = null,
        wardrobePieces: List<ClothingPiece> = listOf(piece(1, "White tee", 10), piece(2, "Blue jeans", 12)),
        onSave: suspend (LocalDate, String?, String?, List<ClothingPiece>) -> Boolean,
    ) {
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    AddOutfitDialog(
                        params =
                            AddOutfitDialogParams(
                                outfit = outfit,
                                clothingTypes = listOf(teeType, jeansType),
                                wardrobePieces = wardrobePieces,
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
    fun `existing outfit pieces are summarised as chips and saved on edit`() =
        runDesktopComposeUiTest(width = 1024, height = 1600) {
            var savedDate: LocalDate? = null
            var savedPieces: List<ClothingPiece>? = null
            setDialog(
                outfit = existingOutfit(piece(1, "White tee", 10), piece(2, "Blue jeans", 12)),
            ) { date, _, _, pieces ->
                savedDate = date
                savedPieces = pieces
                true
            }

            onNodeWithText("Pieces (optional, 2 selected):").assertIsDisplayed()
            onNodeWithText("White tee").assertIsDisplayed()
            onNodeWithText("Blue jeans").assertIsDisplayed()

            onNodeWithText("Save Changes").performClick()
            waitForIdle()

            assertEquals(today, savedDate)
            assertEquals(listOf(1L, 2L), savedPieces?.map { it.id }?.sorted())
        }

    @Test
    fun `choose pieces button is disabled without wardrobe pieces`() =
        runDesktopComposeUiTest(width = 1024, height = 1600) {
            setDialog(wardrobePieces = emptyList()) { _, _, _, _ -> true }

            onNodeWithText("No wardrobe pieces yet. Add some in the Wardrobe tab.").assertIsDisplayed()
            onNodeWithText("Choose pieces").assertIsNotEnabled()
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
            // Title and confirm button both read "Log Outfit"; the button is the second node.
            onAllNodesWithText("Log Outfit")[1].performClick()
            waitForIdle()

            assertEquals(LocalDate(2026, 6, 11), savedDate)
        }
}
