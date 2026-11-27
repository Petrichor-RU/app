package nl.petrichor.app.data.affiliate

import nl.petrichor.app.data.affiliate.adapters.FlipkartPrice
import nl.petrichor.app.data.affiliate.adapters.effectivePrice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlipkartAdapterTest {
    private fun price(
        name: String,
        value: Double?,
        decimalValue: String? = null,
    ) = FlipkartPrice(name = name, value = value, decimalValue = decimalValue, currency = "INR")

    @Test
    fun `a sale item shows the lowest special price not the MRP`() {
        val prices =
            listOf(
                price("MRP", 2999.0, "2,999"),
                price("Selling Price", 1999.0, "1,999"),
                price("Special Price", 1299.0, "1,299"),
            )
        assertEquals("₹ 1,299", prices.effectivePrice())
    }

    @Test
    fun `without a special price the selling price wins over the MRP`() {
        val prices =
            listOf(
                price("MRP", 2999.0, "2,999"),
                price("Selling Price", 1999.0, "1,999"),
            )
        assertEquals("₹ 1,999", prices.effectivePrice())
    }

    @Test
    fun `an item without a discount shows the MRP`() {
        assertEquals("₹ 999", listOf(price("MRP", 999.0, "999")).effectivePrice())
    }

    @Test
    fun `entries without any amount are ignored`() {
        assertNull(emptyList<FlipkartPrice>().effectivePrice())
        assertNull(listOf(price("MRP", null, null)).effectivePrice())
    }
}
