package nl.petrichor.app.ui.wardrobe

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.affiliate.AffiliateItem
import nl.petrichor.app.data.affiliate.AffiliateStore
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.ui.wardrobe.affiliate.AffiliateDetailScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AffiliateDetailScreenTest {
    private fun item(price: String? = "€ 12.00") =
        AffiliateItem(
            id = "https://www.flipkart.com/p/itm123",
            name = "Y2K Gap cropped hoodie top",
            store = AffiliateStore.FLIPKART,
            url = "https://www.flipkart.com/p/itm123",
            imageUrl = null,
            price = price,
            description = "Barely worn, size S.",
            typeName = "Hoodies",
            supertype = ClothingSupertype.UPPER_BODY,
        )

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `detail screen shows store price type and description`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        AffiliateDetailScreen(item = item(), onDismiss = {}, onShop = {})
                    }
                }
            }

            // The hero image pushes the rows below the fold; scroll each into view.
            onNodeWithText("Y2K Gap cropped hoodie top").assertIsDisplayed()
            for (text in listOf("Flipkart", "€ 12.00", "Hoodies", "Barely worn, size S.")) {
                onNode(hasScrollAction()).performScrollToNode(hasText(text))
                onNodeWithText(text).assertIsDisplayed()
            }
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `price row is hidden when the adapter found no price`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        AffiliateDetailScreen(item = item(price = null), onDismiss = {}, onShop = {})
                    }
                }
            }

            assertTrue(onAllNodesWithText("Price").fetchSemanticsNodes().isEmpty())
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `details move beside the photo on wide screens and below it on compact screens`() {
        val sizes = listOf(360 to 800, 599 to 800, 600 to 800, 840 to 800, 1200 to 800, 1200 to 360)
        for ((width, height) in sizes) {
            runDesktopComposeUiTest(width = width, height = height) {
                setContent {
                    MaterialSymbolsRenderingScope {
                        MaterialTheme {
                            AffiliateDetailScreen(item = item(), onDismiss = {}, onShop = {})
                        }
                    }
                }

                val imageBounds = onNodeWithContentDescription("Product image").getUnclippedBoundsInRoot()
                val detailsBounds = onNodeWithText("Details").getUnclippedBoundsInRoot()
                if (width >= 600) {
                    assertTrue(detailsBounds.left > imageBounds.right, "Details should be beside the photo at $width")
                } else {
                    assertTrue(detailsBounds.top > imageBounds.bottom, "Details should be below the photo at $width")
                }
                // The shop button stays reachable in both layouts.
                onNode(hasScrollAction()).performScrollToNode(hasText("Shop at Flipkart"))
                onNodeWithText("Shop at Flipkart").assertIsDisplayed()
            }
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `shop button reports the item through onShop`() =
        runDesktopComposeUiTest {
            var shopped: AffiliateItem? = null
            val item = item()
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        AffiliateDetailScreen(item = item, onDismiss = {}, onShop = { shopped = it })
                    }
                }
            }

            onNode(hasScrollAction()).performScrollToNode(hasText("Shop at Flipkart"))
            onNodeWithText("Shop at Flipkart").assertIsDisplayed().performClick()
            assertEquals(item, shopped)
        }
}
