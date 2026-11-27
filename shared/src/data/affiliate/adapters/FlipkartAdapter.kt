package nl.petrichor.app.data.affiliate.adapters

import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import nl.petrichor.app.AppConfig
import nl.petrichor.app.data.affiliate.AffiliateAdapter
import nl.petrichor.app.data.affiliate.AffiliateItem
import nl.petrichor.app.data.affiliate.AffiliateStore
import nl.petrichor.app.data.affiliate.ShoppingContext
import nl.petrichor.app.data.affiliate.affiliateHttpClient
import nl.petrichor.app.data.affiliate.buildAffiliateItem
import nl.petrichor.app.data.affiliate.currencySymbol
import nl.petrichor.app.data.affiliate.extractJsonObject
import nl.petrichor.app.data.affiliate.getHtml
import nl.petrichor.app.data.affiliate.scrapeJson
import nl.petrichor.app.data.affiliate.typeNameToSearchTerm

/**
 * Flipkart adapter. Prefers the official Flipkart Affiliate API (no scraping) when the affiliate
 * tracking id + API token are configured in AppConfig; otherwise it scrapes the search page,
 * whose product cards are embedded as plain `"productInfo":{...}` JSON blocks (baseUrl, titles,
 * pricing, media). It injects the Flipkart affiliate code into every product URL itself
 * ([affiliateUrl]), so returned items carry ready-to-go affiliate links. Any failure returns what
 * was collected so far — the feed degrades silently.
 */
internal class FlipkartAdapter(
    private val client: HttpClient = affiliateHttpClient(),
    private val affiliateApi: FlipkartAffiliateApi =
        FlipkartAffiliateApi(client, AppConfig.AffiliateCodes.flipkartCode, AppConfig.AffiliateCodes.flipkartAffiliateToken),
) : AffiliateAdapter {
    override suspend fun fetch(
        context: ShoppingContext,
        maxItems: Int,
    ): List<AffiliateItem> {
        val terms = (context.primaryTypeNames + context.fillerTypeNames).map { typeNameToSearchTerm(it) }.distinct()
        val items = mutableListOf<AffiliateItem>()
        for (term in terms.take(MAX_REQUESTS)) {
            // A single term failing (bad URL, changed payload) must not discard items already
            // collected from earlier terms — degrade that term to empty and keep going.
            items +=
                runCatching { fetchTerm(term, maxItems - items.size) }
                    .onFailure {
                        if (it is CancellationException) {
                            currentCoroutineContext().ensureActive()
                        }
                    }.getOrDefault(emptyList())
            if (items.size >= maxItems) break
        }
        return items.distinctBy { it.url }.take(maxItems)
    }

    private suspend fun fetchTerm(
        term: String,
        remaining: Int,
    ): List<AffiliateItem> {
        if (affiliateApi.isConfigured() && remaining > 0) {
            val raw = affiliateApi.search(term, minOf(FlipkartAffiliateApi.RESULT_COUNT, remaining))
            if (raw != null) {
                return raw.mapNotNull { item ->
                    buildAffiliateItem(
                        store = AffiliateStore.FLIPKART,
                        name = item.title,
                        url = affiliateUrl(item.url),
                        imageUrl = item.imageUrl,
                        price = item.price,
                        description = item.description,
                        typeText = "${item.title} $term",
                    )
                }
            }
            // API configured but failed: fall through to the scraper.
        }
        return scrapeTerm(term)
    }

    @Suppress("TooGenericExceptionCaught") // any scrape failure degrades to no items
    private suspend fun scrapeTerm(term: String): List<AffiliateItem> {
        val html =
            try {
                client.getHtml(
                    "https://www.flipkart.com/search?q=${term.encodeURLParameter()}&marketplace=FLIPKART",
                    acceptLanguage = "en-IN,en;q=0.9",
                )
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                return emptyList() // blocked or unreachable: stay silent
            }
        return parseSearchPage(html, term)
    }

    private fun parseSearchPage(
        html: String,
        term: String,
    ): List<AffiliateItem> {
        val items = mutableListOf<AffiliateItem>()
        var fromIndex = 0
        while (items.size < MAX_PARSE_BLOCKS) {
            val markerIndex = html.indexOf(PRODUCT_INFO_MARKER, fromIndex)
            val blockJson = if (markerIndex < 0) null else extractJsonObject(html, PRODUCT_INFO_MARKER, fromIndex)
            if (blockJson == null) break
            // Advance past the extracted block by locating it in the source, robust to any
            // whitespace between the marker and the opening brace.
            fromIndex = html.indexOf(blockJson, markerIndex) + blockJson.length
            parseBlock(blockJson, term)?.let { items += it }
        }
        return items
    }

    /** Parses one `"productInfo":{...}` JSON block; null when the block has no usable product. */
    private fun parseBlock(
        blockJson: String,
        term: String,
    ): AffiliateItem? {
        val value =
            runCatching { scrapeJson.decodeFromString<FlipkartProductInfo>(blockJson) }
                .getOrNull()
                ?.value ?: return null
        val name =
            value.titles
                ?.newTitle
                ?.trim()
                .orEmpty()
                .ifEmpty {
                    value.titles
                        ?.title
                        ?.trim()
                        .orEmpty()
                }
        val path = value.baseUrl
        return if (name.isEmpty() || path.isNullOrBlank()) {
            null
        } else {
            val url =
                when {
                    path.startsWith("https://") -> path
                    path.startsWith("http://") -> path.replaceFirst("http://", "https://")
                    path.startsWith("//") -> "https:$path"
                    else -> "https://www.flipkart.com$path"
                }
            val price = value.pricing?.prices?.effectivePrice()
            val image =
                value.media
                    ?.images
                    ?.firstOrNull()
                    ?.url
                    ?.resolveImageTemplate()
            buildAffiliateItem(
                store = AffiliateStore.FLIPKART,
                name = name,
                url = affiliateUrl(url),
                imageUrl = image,
                price = price,
                description = null,
                typeText = "$name $term",
            )
        }
    }

    /**
     * Injects the Flipkart affiliate tracking id into a product link via [flipkartAffiliateLink].
     * This adapter owns all Flipkart-specific tagging: every item it builds carries a ready-to-go
     * affiliate URL, and the feed/UI just open [AffiliateItem.url] as-is.
     */
    private fun affiliateUrl(url: String): String = flipkartAffiliateLink(url, AppConfig.AffiliateCodes.flipkartCode)

    /** Flipkart image URLs are templates: http://rukmini1.flixcart.com/image/{@width}/{@height}/...?q={@quality}. */
    private fun String.resolveImageTemplate(): String =
        replace("{@width}", "416")
            .replace("{@height}", "416")
            .replace("{@quality}", "90")
            .replace("http://", "https://")

    companion object {
        private const val MAX_REQUESTS = 3
        private const val MAX_PARSE_BLOCKS = 40

        // The key only; extractJsonObject scans forward to the opening '{', tolerating any
        // whitespace or colon formatting Flipkart applies after the key.
        private const val PRODUCT_INFO_MARKER = "\"productInfo\""
    }
}

/**
 * Appends the Flipkart affiliate tracking id to a product link as `?affid=<code>` — the
 * Flipkart-specific tagging lives here, inside the Flipkart adapter, not in shared code.
 * An empty/blank code returns the link unchanged (no-op) — never a broken URL. Only https
 * links are ever decorated, and it is idempotent: links that already carry `affid` (the Affiliate
 * API may return them pre-tagged) are left unchanged, never double-tagged.
 */
internal fun flipkartAffiliateLink(
    link: String,
    code: String?,
): String =
    if (link.startsWith("https://") && !code.isNullOrBlank()) {
        val builder = URLBuilder(link)
        if (builder.parameters["affid"] != null) link else builder.apply { parameters.append("affid", code) }.buildString()
    } else {
        link
    }

/**
 * Formats the effective price from a search-page `pricing.prices` array. The array lists the MRP
 * and — for items on sale — a lower "Special Price"/"Selling Price"; the lowest entry is what the
 * customer actually pays, so it wins over the struck-through before-sale MRP.
 */
internal fun List<FlipkartPrice>.effectivePrice(): String? =
    mapNotNull { p ->
        // Drop entries with no numeric amount (e.g. a non-numeric decimalValue) so they can
        // neither win the minimum nor render as a non-numeric price.
        (p.value ?: p.decimalValue?.replace(",", "")?.toDoubleOrNull())?.let { p to it }
    }.minByOrNull { it.second }
        ?.let { (p, _) ->
            p.decimalValue?.let { d -> "${currencySymbol(p.currency)} $d" }
                ?: p.value?.let { "${currencySymbol(p.currency)} $it" }
        }

@Serializable
private data class FlipkartProductInfo(
    val value: FlipkartProductValue? = null,
)

@Serializable
private data class FlipkartProductValue(
    val baseUrl: String? = null,
    val titles: FlipkartTitles? = null,
    val pricing: FlipkartPricing? = null,
    val media: FlipkartMedia? = null,
)

@Serializable
private data class FlipkartTitles(
    val newTitle: String? = null,
    val title: String? = null,
)

@Serializable
private data class FlipkartPricing(
    val prices: List<FlipkartPrice>? = null,
)

@Serializable
internal data class FlipkartPrice(
    val name: String? = null,
    val value: Double? = null,
    val decimalValue: String? = null,
    val currency: String? = null,
)

@Serializable
private data class FlipkartMedia(
    val images: List<FlipkartImage>? = null,
)

@Serializable
private data class FlipkartImage(
    val url: String? = null,
)
