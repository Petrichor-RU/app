package nl.petrichor.app.ui.wardrobe

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.catbit.material_symbols.MaterialSymbolsRenderingScope
import nl.petrichor.app.data.affiliate.AffiliateItem
import nl.petrichor.app.data.affiliate.AffiliateStore
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.ui.wardrobe.affiliate.SponsoredClothingCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SponsoredClothingCardTest {
    private fun sponsored(name: String = "Winter hoodie") =
        AffiliateItem(
            id = "https://www.flipkart.com/p/itm123",
            name = name,
            store = AffiliateStore.FLIPKART,
            url = "https://www.flipkart.com/p/itm123",
            imageUrl = null, // no network in tests: the placeholder icon path
            price = "€ 12.00",
            description = null,
            typeName = "Hoodies",
            supertype = ClothingSupertype.UPPER_BODY,
        )

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `sponsored card shows the sponsored badge and no price on the card`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        SponsoredClothingCard(item = sponsored(), onClick = {})
                    }
                }
            }

            // The badge label is static text; assert the dynamic semantics instead: the card's
            // content description carries the sponsored item's name.
            onNodeWithContentDescription("Sponsored: Winter hoodie").assertIsDisplayed()
            // Price is shown on the detail screen only, never on the grid card.
            assertTrue(onAllNodesWithText("€ 12.00").fetchSemanticsNodes().isEmpty())
        }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `tapping the sponsored card invokes onClick`() =
        runDesktopComposeUiTest {
            var clicks = 0
            setContent {
                MaterialSymbolsRenderingScope {
                    MaterialTheme {
                        SponsoredClothingCard(item = sponsored(), onClick = { clicks++ })
                    }
                }
            }

            onNodeWithContentDescription("Sponsored: Winter hoodie").performClick()
            assertEquals(1, clicks)
        }
}
