package nl.petrichor.app.data.affiliate

import nl.petrichor.app.data.affiliate.adapters.FlipkartAffiliateApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlipkartAffiliateApiTest {
    private val sampleResponse =
        """
        {
          "products": [
            {
              "productBaseInfoV1": {
                "productId": "SWSABC123",
                "title": "Men Full Sleeve Solid Hoodie",
                "productUrl": "https://www.flipkart.com/jockey-hoodie/p/itm123?pid=SWSABC123",
                "flipkartSellingPrice": {"amount": 1649, "currency": "INR"},
                "productImageUrls": {"400x400": "http://rukmini1.flixcart.com/image/400/400/xif0q/hoodie.jpeg"},
                "productDescription": "Warm fleece-lined hoodie"
              }
            },
            {
              "title": "Plain product without wrapper",
              "productUrl": "https://www.flipkart.com/p/itm456?pid=SWSDEF456",
              "maximumRetailPrice": {"amount": 999.0, "currency": "INR"}
            },
            {
              "productBaseInfoV1": {"productId": "NOURL", "title": "No URL"}
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `parses search response products with url price image and description`() {
        val items = FlipkartAffiliateApi.parseSearchResponse(sampleResponse)
        assertEquals(2, items?.size, "product without URL is dropped")

        val hoodie = items!![0]
        assertEquals("Men Full Sleeve Solid Hoodie", hoodie.title)
        assertEquals("https://www.flipkart.com/jockey-hoodie/p/itm123?pid=SWSABC123", hoodie.url)
        assertEquals("₹ 1649", hoodie.price)
        assertEquals("https://rukmini1.flixcart.com/image/400/400/xif0q/hoodie.jpeg", hoodie.imageUrl)
        assertEquals("Warm fleece-lined hoodie", hoodie.description)

        val plain = items[1]
        assertEquals("₹ 999", plain.price, "MRP fallback, whole-number formatting")
        assertEquals(null, plain.imageUrl)
    }

    @Test
    fun `parses image urls given as an array and upgrades them to https`() {
        val body =
            """
            {
              "products": [
                {
                  "productBaseInfoV1": {
                    "title": "Baseball cap",
                    "productUrl": "https://www.flipkart.com/p/itm789?pid=CAP123",
                    "imageUrls": ["http://rukmini1.flixcart.com/image/400/400/cap.jpeg"]
                  }
                }
              ]
            }
            """.trimIndent()
        val items = FlipkartAffiliateApi.parseSearchResponse(body)
        assertEquals("https://rukmini1.flixcart.com/image/400/400/cap.jpeg", items?.first()?.imageUrl)
    }

    @Test
    fun `a sale item shows the special price not the before-sale price`() {
        val body =
            """
            {
              "products": [
                {
                  "productBaseInfoV1": {
                    "title": "Sale hoodie",
                    "productUrl": "https://www.flipkart.com/p/itm999?pid=SALE1",
                    "maximumRetailPrice": {"amount": 2999.0, "currency": "INR"},
                    "flipkartSellingPrice": {"amount": 1999.0, "currency": "INR"},
                    "flipkartSpecialPrice": {"amount": 1299.0, "currency": "INR"}
                  }
                }
              ]
            }
            """.trimIndent()
        val items = FlipkartAffiliateApi.parseSearchResponse(body)
        assertEquals("₹ 1299", items?.first()?.price, "the lowest (on-sale) price must win over MRP/selling price")
    }

    @Test
    fun `returns null when there is no products array or the body is invalid`() {
        assertNull(FlipkartAffiliateApi.parseSearchResponse("""{"error": "Invalid Headers"}"""))
        assertNull(FlipkartAffiliateApi.parseSearchResponse("not json"))
    }
}
