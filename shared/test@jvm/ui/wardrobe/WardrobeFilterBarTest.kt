package nl.petrichor.app.ui.wardrobe

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.wardrobe.ClothingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WardrobeFilterBarTest {
    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `filter menu exposes all types and clothing types`() = runDesktopComposeUiTest {
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    WardrobeFilterBar(
                        clothingTypes = listOf(
                            ClothingType(id = 1L, name = "T-Shirts", supertype = ClothingSupertype.UPPER_BODY),
                            ClothingType(id = 2L, name = "Jeans", supertype = ClothingSupertype.LOWER_BODY)
                        ),
                        onTypeSelected = {}
                    )
                }
            }
        }

        onNodeWithContentDescription("Filter").performClick()

        onNodeWithText("All Types").assertIsDisplayed()
        onNodeWithText("T-Shirts").assertIsDisplayed()
        onNodeWithText("Jeans").assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `selecting all types reports a null type id`() = runDesktopComposeUiTest {
        var selectedTypeId: Long? = 42L
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    WardrobeFilterBar(
                        clothingTypes = emptyList(),
                        onTypeSelected = { selectedTypeId = it }
                    )
                }
            }
        }

        onNodeWithContentDescription("Filter").performClick()
        onNodeWithText("All Types").performClick()

        assertNull(selectedTypeId)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `selecting a clothing type reports its id`() = runDesktopComposeUiTest {
        var selectedTypeId: Long? = null
        setContent {
            MaterialSymbolsRenderingScope {
                MaterialTheme {
                    WardrobeFilterBar(
                        clothingTypes = listOf(
                            ClothingType(id = 7L, name = "Jackets", supertype = ClothingSupertype.UPPER_BODY)
                        ),
                        onTypeSelected = { selectedTypeId = it }
                    )
                }
            }
        }

        onNodeWithContentDescription("Filter").performClick()
        onNodeWithText("Jackets").performClick()

        assertEquals(7L, selectedTypeId)
    }
}
