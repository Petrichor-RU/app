package nl.petrichor.app.ui.wardrobe

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import nl.petrichor.app.data.wardrobe.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class ClothingDetailScreenTest {
    private val clothing =
        ClothingPiece(
            name = "Blue jacket with a long descriptive name for checking smaller screens",
            typeId = 1L,
            material = Material.COTTON,
            notes = "A favourite jacket.\n".repeat(60),
            imagePath = "",
            thumbnailPath = "",
        )
    private val clothingType = ClothingType(id = 1L, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)

    @Test
    fun `details move beside the photo on wide screens and remain reachable in short windows`() {
        val sizes = listOf(360 to 800, 599 to 800, 600 to 800, 840 to 800, 1200 to 800, 1920 to 900, 1200 to 360)
        for ((width, height) in sizes) {
            runDesktopComposeUiTest(width = width, height = height) {
                setContent {
                    MaterialSymbolsRenderingScope {
                        MaterialTheme {
                            ClothingDetailScreen(clothing, clothingType, onDismiss = {}, onEdit = {}, onDelete = {})
                        }
                    }
                }

                val imageBounds = onNodeWithContentDescription("Clothing image").getUnclippedBoundsInRoot()
                val detailsBounds = onNodeWithText("Details").getUnclippedBoundsInRoot()
                if (width >= 600) {
                    onNodeWithContentDescription("Clothing image").assertIsDisplayed()
                    onNodeWithText("Details").assertIsDisplayed()
                    assertTrue(detailsBounds.left > imageBounds.right, "Details should be beside the photo at $width")
                } else {
                    assertTrue(detailsBounds.top > imageBounds.bottom, "Details should be below the photo at $width")
                }
                onNodeWithText("Notes").performScrollTo().assertIsDisplayed()
                onNodeWithText(clothing.notes!!).performScrollTo().assertIsDisplayed()
                onNodeWithContentDescription("Edit clothing item").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `wide and compact detail layouts preserve their actions`() {
        for (width in listOf(360, 1200)) {
            runDesktopComposeUiTest(width = width, height = 800) {
                var edited = false
                var deleted = false
                var dismissed = false
                var laundryDays = 0
                setContent {
                    MaterialSymbolsRenderingScope {
                        MaterialTheme {
                            ClothingDetailScreen(
                                clothing = clothing,
                                clothingType = clothingType,
                                onDismiss = { dismissed = true },
                                onEdit = { edited = it == clothing },
                                onDelete = { deleted = it == clothing },
                                onMarkLaundry = { laundryDays = it },
                            )
                        }
                    }
                }

                onNodeWithContentDescription("Edit clothing item").performClick()
                assertTrue(edited)
                onNodeWithContentDescription("More options").performClick()
                onNodeWithText("Mark in laundry").performClick()
                onNodeWithText("For 2 days").performClick()
                assertEquals(2, laundryDays)
                onNodeWithContentDescription("More options").performClick()
                onNodeWithText("Delete").performClick()
                assertTrue(deleted)
                onNodeWithContentDescription("Close").performClick()
                assertTrue(dismissed)
            }
        }
    }

    @Test
    fun `laundry row is shown only while the until time is in the future`() {
        val now = Clock.System.now().toEpochMilliseconds()
        for ((until, visible) in listOf(1L to false, now to false, now + 86_400_000L to true)) {
            runDesktopComposeUiTest(width = 360, height = 800) {
                setContent {
                    MaterialSymbolsRenderingScope {
                        MaterialTheme {
                            ClothingDetailScreen(
                                clothing = clothing,
                                clothingType = clothingType,
                                laundryUntilEpochMs = until,
                                onDismiss = {},
                                onEdit = {},
                                onDelete = {},
                            )
                        }
                    }
                }
                if (visible) {
                    onNodeWithText("In laundry until").performScrollTo().assertIsDisplayed()
                } else {
                    onNodeWithText("In laundry until").assertDoesNotExist()
                }
            }
        }
    }
}
