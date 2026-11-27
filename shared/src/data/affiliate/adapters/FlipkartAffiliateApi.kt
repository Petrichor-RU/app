package nl.petrichor.app.data.affiliate.adapters

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import nl.petrichor.app.data.affiliate.arrayAt
import nl.petrichor.app.data.affiliate.currencySymbol
import nl.petrichor.app.data.affiliate.doubleAt
import nl.petrichor.app.data.affiliate.formatPlain
import nl.petrichor.app.data.affiliate.objectAt
import nl.petrichor.app.data.affiliate.scrapeJson
import nl.petrichor.app.data.affiliate.stringAt

/**
 * Client for the official Flipkart Affiliate API (no scraping needed):
 * `GET https://affiliate-api.flipkart.net/affiliate/1.0/search.json?query=<q>&resultCount=<n>`
 * with headers `Fk-Affiliate-Id` (the affiliate tracking id, also appended to product URLs as
 * `?affid=`) and `Fk-Affiliate-Token` (the API token from the Flipkart Affiliate dashboard).
 *
 * Requires both credentials; without them [search] returns null and the caller falls back to
 * scraping / degrades silently.
 */
internal class FlipkartAffiliateApi(
    private val client: HttpClient,
    private val affiliateId: String?,
    private val affiliateToken: String?,
) {
    fun isConfigured(): Boolean = !affiliateId.isNullOrBlank() && !affiliateToken.isNullOrBlank()

    /** One product as returned by the search API, before type mapping. */
    internal data class RawItem(
        val title: String,
        val url: String,
        val imageUrl: String?,
        val price: String?,
        val description: String?,
    )

    /** Keyword search; null on any failure (no credentials, HTTP error, changed payload). */
    suspend fun search(
        query: String,
        resultCount: Int = RESULT_COUNT,
    ): List<RawItem>? = if (isConfigured()) doSearch(query, resultCount) else null

    private suspend fun doSearch(
        query: String,
        resultCount: Int,
    ): List<RawItem>? =
        try {
            val url = "$API_BASE/affiliate/1.0/search.json?query=${query.encodeURLParameter()}&resultCount=$resultCount"
            val response =
                client.get(url) {
                    header("Fk-Affiliate-Id", affiliateId.orEmpty())
                    header("Fk-Affiliate-Token", affiliateToken.orEmpty())
                }
            val body = response.bodyAsText()
            if (response.status.isSuccess()) parseSearchResponse(body) else null
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            null // any API failure degrades to null; the caller falls back to scraping
        }

    companion object {
        const val API_BASE = "https://affiliate-api.flipkart.net"
        const val RESULT_COUNT = 10

        /** Parses a search response; null when the payload has no products array. */
        fun parseSearchResponse(body: String): List<RawItem>? {
            val root = runCatching { scrapeJson.parseToJsonElement(body) }.getOrNull() as? JsonObject
            val products = root?.arrayAt("products")
            return products?.mapNotNull { element -> parseProduct(element as? JsonObject) }
        }

        private fun parseProduct(product: JsonObject?): RawItem? {
            if (product == null) return null
            val info = product.objectAt("productBaseInfoV1") ?: product
            val title = info.stringAt("title") ?: info.stringAt("productName").orEmpty()
            val url = info.stringAt("productUrl")
            return if (title.isNotEmpty() && url != null) {
                RawItem(
                    title = title,
                    url = url,
                    imageUrl = parseImageUrl(info),
                    price = effectiveApiPrice(info),
                    description = info.stringAt("productDescription") ?: info.stringAt("description"),
                )
            } else {
                null
            }
        }

        /** `productImageUrls`/`imageUrls` is a size→url map or a plain array; take the first URL and upgrade it to https. */
        private fun parseImageUrl(info: JsonObject): String? {
            val images = info["productImageUrls"] ?: info["imageUrls"] ?: return null
            val candidates =
                when (images) {
                    is JsonObject -> images.values.mapNotNull { (it as? JsonPrimitive)?.content }
                    is JsonArray -> images.mapNotNull { (it as? JsonPrimitive)?.content }
                    else -> emptyList()
                }
            return candidates.firstOrNull()?.replace("http://", "https://")
        }

        /**
         * Formats the effective price from `productBaseInfoV1`: sale items carry a
         * `flipkartSpecialPrice` below the `flipkartSellingPrice`/MRP, so the lowest of the
         * available price objects is what the customer actually pays.
         */
        internal fun effectiveApiPrice(info: JsonObject): String? {
            val best =
                listOfNotNull(
                    info.objectAt("flipkartSpecialPrice"),
                    info.objectAt("flipkartSellingPrice"),
                    info.objectAt("maximumRetailPrice"),
                ).mapNotNull { priceObj -> priceObj.doubleAt("amount")?.let { priceObj to it } }
                    .minByOrNull { it.second }
                    ?.first
            val amount = best?.doubleAt("amount") ?: return null
            return "${currencySymbol(best.stringAt("currency"))} ${amount.formatPlain()}"
        }
    }
}
