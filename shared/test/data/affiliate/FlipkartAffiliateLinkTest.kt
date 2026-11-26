package nl.petrichor.app.data.affiliate

import nl.petrichor.app.data.affiliate.adapters.flipkartAffiliateLink
import kotlin.test.Test
import kotlin.test.assertEquals

class FlipkartAffiliateLinkTest {
    @Test
    fun `empty or blank code leaves the link unchanged`() {
        val link = "https://www.flipkart.com/p/itm1?pid=X"
        assertEquals(link, flipkartAffiliateLink(link, null))
        assertEquals(link, flipkartAffiliateLink(link, ""))
        assertEquals(link, flipkartAffiliateLink(link, "  "))
    }

    @Test
    fun `appends the code as affid and preserves existing parameters`() {
        assertEquals(
            "https://www.flipkart.com/p/itm1?pid=X&affid=fk-1",
            flipkartAffiliateLink("https://www.flipkart.com/p/itm1?pid=X", "fk-1"),
        )
    }

    @Test
    fun `non-https links are never decorated`() {
        val link = "http://www.flipkart.com/p/itm1"
        assertEquals(link, flipkartAffiliateLink(link, "fk-1"))
    }

    @Test
    fun `links that already carry affid are left unchanged`() {
        // Flipkart Affiliate API productUrl values may come pre-tagged.
        val tagged = "https://www.flipkart.com/p/itm1?pid=X&affid=fk-1"
        assertEquals(tagged, flipkartAffiliateLink(tagged, "fk-1"))
    }
}
