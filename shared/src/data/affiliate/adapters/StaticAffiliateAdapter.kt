package nl.petrichor.app.data.affiliate.adapters

import nl.petrichor.app.data.affiliate.AffiliateAdapter
import nl.petrichor.app.data.affiliate.AffiliateItem
import nl.petrichor.app.data.affiliate.AffiliateStore
import nl.petrichor.app.data.affiliate.ShoppingContext
import nl.petrichor.app.data.affiliate.StoreAdapter
import nl.petrichor.app.data.affiliate.buildAffiliateItem
import nl.petrichor.app.data.affiliate.mapToBuiltInType

/**
 * Static adapter for hand-picked recommendations listed in [STATIC_ENTRIES]: no network, no
 * scraping, cannot be blocked or fail. Not tied to one store — entries may use any
 * [AffiliateStore]; [staticAdapters] groups them per store so the feed country-gates each group
 * by its own store. Links are used exactly as listed — they already carry the affiliate-network
 * redirect (e.g. a sovrn.co link), so they are ready-to-go affiliate URLs and must never be
 * crawled; product images are listed per entry, either as a remote [StaticAffiliateEntry.imageUrl]
 * or bundled as [StaticAffiliateEntry.imageBase64]. Entries with a non-https
 * [StaticAffiliateEntry.url] or an unmappable [StaticAffiliateEntry.typeText] are dropped by
 * [buildAffiliateItem], like any other adapter's.
 */
class StaticAffiliateAdapter(
    private val entries: List<StaticAffiliateEntry> = STATIC_ENTRIES,
) : AffiliateAdapter {
    override suspend fun fetch(
        context: ShoppingContext,
        maxItems: Int,
    ): List<AffiliateItem> =
        entries
            .filter { it.isRelevantTo(context) }
            .mapNotNull { entry ->
                buildAffiliateItem(
                    store = entry.store,
                    name = entry.name,
                    url = entry.url,
                    imageUrl = entry.imageUrl,
                    imageBase64 = entry.imageBase64,
                    price = entry.price,
                    description = entry.description,
                    typeText = entry.typeText,
                )
            }.take(maxItems)
}

/**
 * One [StoreAdapter] per store present in [entries], in first-appearance order, so the feed runs
 * each static group only when that store serves the user's country. Used ahead of the scraped
 * adapters in the feed's list, which is what gives static recommendations their precedence.
 */
internal fun staticAdapters(entries: List<StaticAffiliateEntry> = STATIC_ENTRIES): List<StoreAdapter> =
    entries
        .groupBy { it.store }
        .map { (store, grouped) -> StoreAdapter(store, StaticAffiliateAdapter(grouped)) }

/**
 * One hand-picked recommendation. [alwaysShow] = true ignores the [ShoppingContext] (curated
 * picks always show); false shows the item only when its mapped built-in type is among the
 * context's primary or filler types.
 */
data class StaticAffiliateEntry(
    val store: AffiliateStore,
    val name: String,
    val url: String,
    val imageUrl: String?,
    /**
     * Bundled base64 photo, so the card renders with no network request (offline, WASM/CORS).
     * Takes precedence over [imageUrl] when both are set.
     */
    val imageBase64: String? = null,
    /** Display-ready price (e.g. "€22.50"); shown on the detail screen only, never on the card. */
    val price: String?,
    val description: String?,
    val typeText: String,
    val alwaysShow: Boolean = false,
) {
    fun isRelevantTo(context: ShoppingContext): Boolean {
        val typeName = mapToBuiltInType(typeText)?.first
        return alwaysShow ||
            (typeName != null && typeName in context.primaryTypeNames + context.fillerTypeNames)
    }
}

/**
 * The hand-picked recommendations, in display order. Add entries here to pin more products;
 * restrict their visibility per country via the entry's [AffiliateStore.supportedCountries].
 */
internal val STATIC_ENTRIES: List<StaticAffiliateEntry> =
    listOf(
        StaticAffiliateEntry(
            store = AffiliateStore.HM,
            name = "H&M Jeans",
            url = "https://sovrn.co/6d09ikj",
            imageUrl = null,
            imageBase64 = HM_JEANS_IMAGE_BASE64,
            price = "€22.50",
            description = null,
            typeText = "Jeans",
            alwaysShow = true,
        ),
    )
