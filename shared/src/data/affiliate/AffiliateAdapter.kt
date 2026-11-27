package nl.petrichor.app.data.affiliate

/**
 * The store adapter. Adapters are thick: each decides its own query strategy for its store,
 * scrapes (or calls the store's official API), maps the store taxonomy onto the built-in
 * clothing types (unmappable items are dropped) and returns only https product links already
 * carrying the store's affiliate code (the adapter injects it itself — store-specific tagging is
 * the adapter's job, not the feed's), already relevant to [context]. Adapters must not surface
 * errors to the UI — [AffiliateFeed] catches everything and degrades silently.
 */
fun interface AffiliateAdapter {
    suspend fun fetch(
        context: ShoppingContext,
        maxItems: Int,
    ): List<AffiliateItem>
}

/**
 * Binds a [store] to the [adapter] serving it. The feed runs the bindings whose store serves the
 * user's country in list order and merges their items in that order, so bindings earlier in the
 * list take precedence for the limited sponsored slots (e.g. hand-picked static recommendations
 * before scraped store results).
 */
data class StoreAdapter(
    val store: AffiliateStore,
    val adapter: AffiliateAdapter,
)

/**
 * Builds an [AffiliateItem], dropping it when the link is not https or the store text cannot be
 * mapped onto a built-in clothing type (such items MUST be dropped). The [url] is used exactly
 * as given: each adapter injects its own store-specific affiliate code *before* calling this
 * (see e.g. the Flipkart adapter), so the returned [AffiliateItem.url] is a ready-to-go affiliate
 * link — callers open it as-is.
 */
@Suppress("LongParameterList") // one field per AffiliateItem property; a builder would obscure the mapping
internal fun buildAffiliateItem(
    store: AffiliateStore,
    name: String,
    url: String,
    imageUrl: String?,
    imageBase64: String? = null,
    price: String?,
    description: String?,
    typeText: String,
): AffiliateItem? {
    val type = mapToBuiltInType(typeText)
    return if (!url.startsWith("https://") || type == null) {
        null
    } else {
        AffiliateItem(
            id = url,
            name = name,
            store = store,
            url = url,
            imageUrl = imageUrl?.takeIf { it.startsWith("https://") },
            imageBase64 = imageBase64,
            price = price,
            description = description,
            typeName = type.first,
            supertype = type.second,
        )
    }
}

/** Store search term for a built-in type name, generic fallback included. */
internal fun typeNameToSearchTerm(typeName: String): String =
    typeName
        .lowercase()
        .removeSuffix("s")
        .replace("-", " ")
