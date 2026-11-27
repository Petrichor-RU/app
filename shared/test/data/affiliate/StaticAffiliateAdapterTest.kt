package nl.petrichor.app.data.affiliate

import kotlinx.coroutines.test.runTest
import nl.petrichor.app.data.affiliate.adapters.STATIC_ENTRIES
import nl.petrichor.app.data.affiliate.adapters.StaticAffiliateAdapter
import nl.petrichor.app.data.affiliate.adapters.StaticAffiliateEntry
import nl.petrichor.app.data.affiliate.adapters.staticAdapters
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaticAffiliateAdapterTest {
    private val warmDry = ShoppingContext(rainExpected = false, temperatureBand = TemperatureBand.WARM)
    private val coldRain = ShoppingContext(rainExpected = true, temperatureBand = TemperatureBand.COLD)

    private fun entry(
        url: String = "https://example.com/p/1",
        typeText: String = "Jeans",
        alwaysShow: Boolean = false,
    ) = StaticAffiliateEntry(
        store = AffiliateStore.HM,
        name = "Picked item",
        url = url,
        imageUrl = null,
        price = null,
        description = null,
        typeText = typeText,
        alwaysShow = alwaysShow,
    )

    private fun scrapedItem(
        name: String,
        url: String,
    ) = AffiliateItem(
        id = url,
        name = name,
        store = AffiliateStore.FLIPKART,
        url = url,
        imageUrl = null,
        price = null,
        description = null,
        typeName = "Hoodies",
        supertype = ClothingSupertype.UPPER_BODY,
    )

    private fun feed(
        adapters: List<StoreAdapter>,
        countryCode: String?,
        maxItems: Int = MAX_AFFILIATE_ITEMS,
    ) = AffiliateFeed(
        adapters = adapters,
        maxItems = maxItems,
        resolveCountryCode = { countryCode },
    )

    private val amsterdam = Place("Amsterdam", Coordinates(52.3, 4.9), countryCode = "NL")

    // --- the shipped hand-picked entry ---

    @Test
    fun `the shipped entry is an always-show H and M jeans item carrying the sovrn link as-is`() =
        runTest {
            val items = StaticAffiliateAdapter().fetch(warmDry, MAX_AFFILIATE_ITEMS)
            assertEquals(1, items.size)
            val item = items.single()
            assertEquals("https://sovrn.co/6d09ikj", item.url)
            assertEquals(item.url, item.id)
            assertEquals(AffiliateStore.HM, item.store)
            assertEquals("H&M", item.store.displayName)
            assertEquals("Jeans", item.typeName)
            assertEquals(ClothingSupertype.LOWER_BODY, item.supertype)
            assertEquals("€22.50", item.price)
        }

    @Test
    fun `the shipped entry bundles its photo as base64 so the card needs no network`() =
        runTest {
            val item = StaticAffiliateAdapter().fetch(warmDry, MAX_AFFILIATE_ITEMS).single()
            assertNull(item.imageUrl)
            val bytes = Base64.decode(assertNotNull(item.imageBase64).filterNot { it.isWhitespace() })
            // JPEG magic bytes (FF D8) — the bundled photo is a real image, not a stub.
            assertEquals(0xFF.toByte(), bytes[0])
            assertEquals(0xD8.toByte(), bytes[1])
            assertTrue(bytes.size > 40_000, "expected the card-sized JPEG, got ${bytes.size} bytes")
        }

    @Test
    fun `the shipped entry always shows whatever the shopping context`() =
        runTest {
            val adapter = StaticAffiliateAdapter()
            for (context in listOf(warmDry, coldRain, ShoppingContext(false, TemperatureBand.MILD))) {
                assertEquals(1, adapter.fetch(context, MAX_AFFILIATE_ITEMS).size)
            }
        }

    // --- entry-level behaviour ---

    @Test
    fun `a context-gated entry shows only when the context targets its type`() =
        runTest {
            val tee = StaticAffiliateAdapter(listOf(entry(typeText = "T-Shirt", alwaysShow = false)))
            // WARM + dry targets T-Shirts among the primaries.
            assertEquals(1, tee.fetch(warmDry, MAX_AFFILIATE_ITEMS).size)
            // COLD targets Hoodies/Sweaters/Jackets — the tee stays hidden.
            assertEquals(0, tee.fetch(coldRain, MAX_AFFILIATE_ITEMS).size)
            // Filler types (e.g. Caps) are allowed in every context.
            val cap = StaticAffiliateAdapter(listOf(entry(url = "https://example.com/cap", typeText = "Cap", alwaysShow = false)))
            assertEquals(1, cap.fetch(coldRain, MAX_AFFILIATE_ITEMS).size)
        }

    @Test
    fun `entries with a non-https url or an unmappable type are dropped`() =
        runTest {
            val adapter =
                StaticAffiliateAdapter(
                    listOf(
                        entry(url = "http://insecure.example/x", alwaysShow = true),
                        entry(url = "https://example.com/y", typeText = "kitchen sink", alwaysShow = true),
                    ),
                )
            assertEquals(emptyList(), adapter.fetch(warmDry, MAX_AFFILIATE_ITEMS))
        }

    @Test
    fun `maxItems caps the static items`() =
        runTest {
            val adapter =
                StaticAffiliateAdapter(
                    listOf(
                        entry(url = "https://example.com/1", alwaysShow = true),
                        entry(url = "https://example.com/2", alwaysShow = true),
                        entry(url = "https://example.com/3", alwaysShow = true),
                    ),
                )
            assertEquals(2, adapter.fetch(warmDry, 2).size)
        }

    // --- feed integration: precedence and worldwide gating ---

    @Test
    fun `static items take precedence and scraped items fill the remaining slots`() =
        runTest {
            val scraped =
                AffiliateAdapter { _, _ ->
                    listOf(
                        scrapedItem("Scraped 1", "https://www.flipkart.com/s1"),
                        scrapedItem("Scraped 2", "https://www.flipkart.com/s2"),
                    )
                }
            val feed =
                feed(
                    adapters =
                        listOf(
                            StoreAdapter(AffiliateStore.HM, StaticAffiliateAdapter()),
                            StoreAdapter(AffiliateStore.FLIPKART, scraped),
                        ),
                    countryCode = "IN",
                    maxItems = 2,
                )
            val urls = feed.fetch(amsterdam, null).map { it.url }
            assertEquals(listOf("https://sovrn.co/6d09ikj", "https://www.flipkart.com/s1"), urls)
        }

    @Test
    fun `with a single slot only the static item is shown`() =
        runTest {
            val feed =
                feed(
                    adapters =
                        listOf(
                            StoreAdapter(AffiliateStore.HM, StaticAffiliateAdapter()),
                            StoreAdapter(AffiliateStore.FLIPKART) { _, _ -> listOf(scrapedItem("Scraped", "https://www.flipkart.com/s1")) },
                        ),
                    countryCode = "IN",
                    maxItems = 1,
                )
            assertEquals(listOf("https://sovrn.co/6d09ikj"), feed.fetch(amsterdam, null).map { it.url })
        }

    @Test
    fun `a failing lower-precedence adapter never hides the static items`() =
        runTest {
            val feed =
                feed(
                    adapters =
                        listOf(
                            StoreAdapter(AffiliateStore.HM, StaticAffiliateAdapter()),
                            StoreAdapter(AffiliateStore.FLIPKART) { _, _ -> error("blocked") },
                        ),
                    countryCode = "IN",
                )
            assertEquals(listOf("https://sovrn.co/6d09ikj"), feed.fetch(amsterdam, null).map { it.url })
        }

    @Test
    fun `a worldwide store serves any resolved country and the other adapters stay gated`() =
        runTest {
            for (country in listOf("NL", "US", "DE", "IN")) {
                var scrapedCalled = false
                val feed =
                    feed(
                        adapters =
                            listOf(
                                StoreAdapter(AffiliateStore.HM, StaticAffiliateAdapter()),
                                StoreAdapter(AffiliateStore.FLIPKART) { _, _ ->
                                    scrapedCalled = true
                                    listOf(scrapedItem("Scraped", "https://www.flipkart.com/s1"))
                                },
                            ),
                        countryCode = country,
                    )
                val items = feed.fetch(amsterdam, null)
                // The static item always comes first; Flipkart only fills a slot for IN.
                val expected =
                    if (country == "IN") {
                        listOf("https://sovrn.co/6d09ikj", "https://www.flipkart.com/s1")
                    } else {
                        listOf("https://sovrn.co/6d09ikj")
                    }
                assertEquals(expected, items.map { it.url }, "country=$country")
                assertEquals(country == "IN", scrapedCalled, "Flipkart must only run for IN, country=$country")
            }
        }

    @Test
    fun `a worldwide store serves even an unresolved country`() =
        runTest {
            var scrapedCalled = false
            val feed =
                feed(
                    adapters =
                        listOf(
                            StoreAdapter(AffiliateStore.HM, StaticAffiliateAdapter()),
                            StoreAdapter(AffiliateStore.FLIPKART) { _, _ ->
                                scrapedCalled = true
                                listOf(scrapedItem("Scraped", "https://www.flipkart.com/s1"))
                            },
                        ),
                    countryCode = null,
                )
            assertEquals(listOf("https://sovrn.co/6d09ikj"), feed.fetch(amsterdam, null).map { it.url })
            assertFalse(scrapedCalled)
        }

    @Test
    fun `urls duplicated across adapters keep the higher-precedence item`() =
        runTest {
            val feed =
                feed(
                    adapters =
                        listOf(
                            StoreAdapter(AffiliateStore.HM, StaticAffiliateAdapter()),
                            StoreAdapter(AffiliateStore.FLIPKART) { _, _ -> listOf(scrapedItem("Duplicate", "https://sovrn.co/6d09ikj")) },
                        ),
                    countryCode = "IN",
                )
            val items = feed.fetch(amsterdam, null)
            assertEquals(1, items.size)
            assertEquals(AffiliateStore.HM, items.single().store)
        }

    @Test
    fun `the shipped static entries are all https and mappable so none are dropped`() =
        runTest {
            val adapter = StaticAffiliateAdapter(STATIC_ENTRIES)
            assertTrue(STATIC_ENTRIES.isNotEmpty())
            assertEquals(STATIC_ENTRIES.size, adapter.fetch(warmDry, MAX_AFFILIATE_ITEMS).size)
        }

    // --- multiple stores in the static list: each store's entries follow that store's countries ---

    @Test
    fun `static entries from different stores are each gated by their own store's countries`() =
        runTest {
            val entries =
                listOf(
                    entry(url = "https://hm.example/jeans", alwaysShow = true), // HM: worldwide
                    StaticAffiliateEntry(
                        store = AffiliateStore.FLIPKART,
                        name = "Flipkart pick",
                        url = "https://www.flipkart.com/pick",
                        imageUrl = null,
                        price = null,
                        description = null,
                        typeText = "Hoodie",
                        alwaysShow = true,
                    ),
                )
            val expectedByCountry =
                mapOf(
                    "NL" to listOf("https://hm.example/jeans"),
                    "IN" to listOf("https://hm.example/jeans", "https://www.flipkart.com/pick"),
                    null to listOf("https://hm.example/jeans"), // unresolved: only worldwide stores
                )
            for ((country, expected) in expectedByCountry) {
                val feed = feed(staticAdapters(entries), country)
                assertEquals(expected, feed.fetch(amsterdam, null).map { it.url }, "country=$country")
            }
        }
}
