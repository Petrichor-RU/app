package nl.petrichor.app.ui.wardrobe

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AddClothingDialogTest {
    private fun DesktopComposeUiTest.setDialog(onSave: suspend (ClothingPiece) -> Unit) {
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    AddClothingDialog(
                        clothing =
                            ClothingPiece(
                                name = "White tee",
                                typeId = 10,
                                material = Material.COTTON,
                                notes = null,
                                imagePath = "",
                                thumbnailPath = "",
                            ),
                        clothingTypes = listOf(ClothingType(id = 10, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY)),
                        onDismiss = {},
                        onSave = onSave,
                    )
                }
            }
        }
    }

    @Test
    fun `ordinary save failure is shown and allows retry`() {
        assertRetryableFailure(IllegalStateException("Storage unavailable"))
    }

    @Test
    fun `inner cancellation while dialog is active is shown and allows retry`() {
        assertRetryableFailure(CancellationException("Inner operation cancelled"))
    }

    private fun assertRetryableFailure(failure: Exception) =
        runDesktopComposeUiTest(width = 1024, height = 1600) {
            var attempts = 0
            setDialog {
                attempts++
                if (attempts == 1) throw failure
            }

            onNodeWithText("Save Changes").performClick()
            onNodeWithText("Could not save clothing image: ${failure.message}").assertIsDisplayed()
            onNodeWithText("Save Changes").assertIsEnabled().performClick()
            waitForIdle()

            assertEquals(2, attempts)
        }

    @Test
    fun `cancelling the save job clears busy state without displaying an error`() {
        assertCancelledSave { awaitCancellation() }
    }

    @Test
    fun `ordinary failure after job cancellation does not display an error`() {
        assertCancelledSave { throw IllegalStateException("Storage unavailable") }
    }

    private fun assertCancelledSave(fail: suspend () -> Nothing) =
        runDesktopComposeUiTest(width = 1024, height = 1600) {
            var attempts = 0
            setDialog {
                attempts++
                if (attempts == 1) {
                    currentCoroutineContext().cancel(CancellationException("Save cancelled"))
                    fail()
                }
            }

            onNodeWithText("Save Changes").performClick()
            onNodeWithText("Could not save clothing image: Save cancelled").assertDoesNotExist()
            onNodeWithText("Could not save clothing image: Storage unavailable").assertDoesNotExist()
            onNodeWithText("Save Changes").assertIsEnabled().performClick()
            waitForIdle()

            assertEquals(2, attempts)
        }
}
