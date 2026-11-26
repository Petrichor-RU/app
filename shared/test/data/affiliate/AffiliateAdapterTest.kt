package nl.petrichor.app.data.affiliate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [buildAffiliateItem] is store-agnostic: it uses the url exactly as the adapter passed it.
 * Affiliate-code injection is the adapter's job (the Flipkart adapter tags urls via
 * flipkartAffiliateLink before calling this — covered by FlipkartAffiliateLinkTest).
 */
class AffiliateAdapterTest {
    private val baseUrl = "https://www.flipkart.com/p/itm1?pid=X"

    @Test
    fun `buildAffiliateItem keeps an already affiliate-tagged url unchanged`() {
        val tagged = "$baseUrl&affid=fk-1"
        val item =
            buildAffiliateItem(
                store = AffiliateStore.FLIPKART,
                name = "Hoodie",
                url = tagged,
                imageUrl = null,
                price = null,
                description = null,
                typeText = "Cotton T-Shirt",
            )
        assertEquals(tagged, item?.url)
        assertEquals(tagged, item?.id)
    }

    @Test
    fun `buildAffiliateItem keeps a plain url unchanged`() {
        val item =
            buildAffiliateItem(
                store = AffiliateStore.FLIPKART,
                name = "Hoodie",
                url = baseUrl,
                imageUrl = null,
                price = null,
                description = null,
                typeText = "Cotton T-Shirt",
            )
        assertEquals(baseUrl, item?.url)
        assertEquals(baseUrl, item?.id)
    }

    @Test
    fun `buildAffiliateItem drops non-https urls`() {
        val item =
            buildAffiliateItem(
                store = AffiliateStore.FLIPKART,
                name = "Hoodie",
                url = "http://www.flipkart.com/p/itm1",
                imageUrl = null,
                price = null,
                description = null,
                typeText = "Cotton T-Shirt",
            )
        assertNull(item)
    }

    @Test
    fun `buildAffiliateItem drops unmappable types`() {
        val item =
            buildAffiliateItem(
                store = AffiliateStore.FLIPKART,
                name = "Mystery",
                url = baseUrl,
                imageUrl = null,
                price = null,
                description = null,
                typeText = "kitchen sink",
            )
        assertNull(item)
    }
}
