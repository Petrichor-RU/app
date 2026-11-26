package nl.petrichor.app.data.affiliate

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import nl.petrichor.app.data.wardrobe.ClothingPiece
import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.ForecastDay
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.ui.wardrobe.affiliate.WardrobeGridEntry
import nl.petrichor.app.ui.wardrobe.affiliate.mergeSponsoredIntoGrid
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class AffiliateFeedTest {
    private fun item(
        name: String,
        url: String = "https://www.flipkart.com/p/${name.replace(" ", "-")}",
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
        adapter: AffiliateAdapter,
        countryCode: String? = "IN",
        now: () -> Instant = { Instant.fromEpochSeconds(0) },
        cacheTtl: Duration = 1.hours,
    ) = AffiliateFeed(
        adapter = adapter,
        maxItems = MAX_AFFILIATE_ITEMS,
        resolveCountryCode = { countryCode },
        now = now,
        cacheTtl = cacheTtl,
    )

    private val mumbai = Place("Mumbai", Coordinates(19.0, 72.8), countryCode = "IN")

    @Test
    fun `fetch returns the adapter items deduplicated by url and capped at max`() =
        runTest {
            val items =
                (1..8).map { item("Hoodie $it", "https://www.flipkart.com/p/h$it") } +
                    item(
                        "Hoodie 1",
                        "https://www.flipkart.com/p/h1",
                    )
            val feed = feed({ _, _ -> items })
            val result = feed.fetch(mumbai, null)
            assertEquals(MAX_AFFILIATE_ITEMS, result.size)
            assertEquals(MAX_AFFILIATE_ITEMS, result.map { it.url }.distinct().size)
        }

    @Test
    fun `a failing adapter degrades silently to an empty list`() =
        runTest {
            val feed = feed({ _, _ -> throw IllegalStateException("blocked") })
            assertEquals(emptyList(), feed.fetch(mumbai, null))
        }

    @Test
    fun `a feed timeout degrades to an empty list instead of throwing`() =
        runTest {
            val feed = feed({ _, _ -> awaitCancellation() })
            assertEquals(emptyList(), feed.fetch(mumbai, null))
        }

    @Test
    fun `a supported country runs the adapter`() =
        runTest {
            var called = false
            val feed =
                feed(
                    { _, _ ->
                        called = true
                        listOf(item("Hoodie"))
                    },
                    countryCode = "IN",
                )
            assertEquals(1, feed.fetch(mumbai, null).size)
            assertTrue(called)
        }

    @Test
    fun `an unsupported or unknown country hides the sponsored items and never calls the adapter`() =
        runTest {
            for (country in listOf("NL", "US", null)) {
                var called = false
                val feed =
                    feed(
                        { _, _ ->
                            called = true
                            listOf(item("Hoodie"))
                        },
                        countryCode = country,
                    )
                assertEquals(
                    emptyList(),
                    feed.fetch(Place("Amsterdam", Coordinates(52.3, 4.9), countryCode = country), null),
                )
                assertFalse(called, "adapter must not run for country=$country")
            }
        }

    @Test
    fun `feed derives the shopping context from the selected location's forecast`() =
        runTest {
            var seenContext: ShoppingContext? = null
            val feed =
                feed(
                    { context, _ ->
                        seenContext = context
                        emptyList()
                    },
                )
            val weather =
                WeatherSnapshot(
                    place = mumbai,
                    temperature = 12,
                    feelsLike = 10,
                    description = "Clear sky",
                    icon = "01d",
                    conditionId = 800,
                    humidity = 60,
                    windSpeed = 5,
                    forecast = listOf(ForecastDay("2026-11-25", min = 1, max = 6, icon = "10d", conditionId = 500)),
                )
            feed.fetch(mumbai, weather)
            assertEquals(TemperatureBand.COLD, seenContext?.temperatureBand)
            assertEquals(true, seenContext?.rainExpected)
        }

    @Test
    fun `null weather degrades to the neutral shopping context`() =
        runTest {
            var seenContext: ShoppingContext? = null
            val feed =
                feed(
                    { context, _ ->
                        seenContext = context
                        emptyList()
                    },
                )
            feed.fetch(mumbai, null)
            assertEquals(TemperatureBand.MILD, seenContext?.temperatureBand)
            assertFalse(seenContext?.rainExpected == true)
        }

    // --- advert cache: keyed by selected place + resolved country, ~1 h TTL, in-memory only ---

    @Test
    fun `cached adverts are reused within the TTL without calling the adapter again`() =
        runTest {
            var calls = 0
            var misses = 0
            var current = Instant.fromEpochSeconds(1_000)
            val feed =
                feed(
                    { _, _ ->
                        calls++
                        listOf(item("Hoodie"))
                    },
                    now = { current },
                )
            val first = feed.fetch(mumbai, null) { misses++ }
            current = Instant.fromEpochSeconds(1_000 + 59 * 60) // 59 min later: still fresh
            val second = feed.fetch(mumbai, null) { misses++ }
            assertEquals(1, calls, "adapter must not run again within the TTL")
            assertEquals(first, second)
            assertEquals(1, misses, "onCacheMiss must fire only on the miss")
        }

    @Test
    fun `the cache expires after the TTL and refetches`() =
        runTest {
            var calls = 0
            var misses = 0
            var current = Instant.fromEpochSeconds(1_000)
            val feed =
                feed(
                    { _, _ ->
                        calls++
                        listOf(item("Hoodie $calls"))
                    },
                    now = { current },
                )
            feed.fetch(mumbai, null) { misses++ }
            current = Instant.fromEpochSeconds(1_000 + 61 * 60) // 61 min later: expired
            val second = feed.fetch(mumbai, null) { misses++ }
            assertEquals(2, calls, "adapter must run again after the TTL")
            assertEquals(listOf(item("Hoodie 2")), second)
            assertEquals(2, misses)
        }

    @Test
    fun `switching the selected place invalidates the cache`() =
        runTest {
            var calls = 0
            val feed =
                feed(
                    { _, _ ->
                        calls++
                        listOf(item("Hoodie"))
                    },
                )
            feed.fetch(mumbai, null)
            val delhi = Place("Delhi", Coordinates(28.6, 77.2), countryCode = "IN")
            feed.fetch(delhi, null)
            assertEquals(2, calls, "a new selected place must refetch even within the TTL")
        }

    @Test
    fun `switching the resolved country invalidates the cache`() =
        runTest {
            var calls = 0
            var country: String? = "IN"
            val feed =
                AffiliateFeed(
                    adapter =
                        { _, _ ->
                            calls++
                            listOf(item("Hoodie"))
                        },
                    maxItems = MAX_AFFILIATE_ITEMS,
                    resolveCountryCode = { country },
                    now = { Instant.fromEpochSeconds(1_000) },
                )
            feed.fetch(mumbai, null)
            country = "NL"
            assertEquals(emptyList(), feed.fetch(mumbai, null))
            assertEquals(1, calls, "an unsupported country must not run the adapter")
            country = "IN"
            feed.fetch(mumbai, null)
            assertEquals(2, calls, "returning to a supported country must refetch (key changed)")
        }

    @Test
    fun `a failed fetch is not cached and the next refresh retries`() =
        runTest {
            var calls = 0
            val feed =
                feed(
                    { _, _ ->
                        calls++
                        if (calls == 1) error("blocked") else listOf(item("Hoodie"))
                    },
                )
            assertEquals(emptyList(), feed.fetch(mumbai, null))
            assertEquals(1, feed.fetch(mumbai, null).size)
            assertEquals(2, calls, "the failure must not be cached")
        }

    @Test
    fun `an empty adapter result is not cached and the next fetch retries`() =
        runTest {
            var calls = 0
            val feed =
                feed(
                    { _, _ ->
                        calls++
                        emptyList() // e.g. a transient bot-blocked scrape
                    },
                )
            assertEquals(emptyList(), feed.fetch(mumbai, null))
            assertEquals(emptyList(), feed.fetch(mumbai, null))
            assertEquals(2, calls, "an empty result may be transient and must not be cached for the TTL")
        }

    @Test
    fun `concurrent fetches share a single adapter call via the cache`() =
        runTest {
            var calls = 0
            val feed =
                feed(
                    { _, _ ->
                        calls++
                        listOf(item("Hoodie"))
                    },
                )
            // The app-shell pre-warm and the wardrobe refresh can race; the mutex must
            // serialize them so the second caller serves from the cache instead of
            // double-hitting the store and racing the cache write.
            val first = async { feed.fetch(mumbai, null) }
            val second = async { feed.fetch(mumbai, null) }
            assertEquals(listOf(item("Hoodie")), first.await())
            assertEquals(listOf(item("Hoodie")), second.await())
            assertEquals(1, calls, "the second concurrent fetch must hit the cache")
        }

    // --- country resolution: real device location first, selected place as fallback ---

    @Test
    fun `device location wins over the selected place`() =
        runTest {
            val device = Coordinates(52.3, 4.9)
            val code =
                defaultCountryCode(
                    place = mumbai,
                    deviceCoordinates = { device },
                    reverseGeocode = { if (it == device) "NL" else "IN" },
                )
            assertEquals("NL", code)
        }

    @Test
    fun `without device geolocation the selected place's own country is used`() =
        runTest {
            var reverseGeocoded = false
            val code =
                defaultCountryCode(
                    place = mumbai,
                    deviceCoordinates = { null },
                    reverseGeocode = {
                        reverseGeocoded = true
                        "XX"
                    },
                )
            assertEquals("IN", code)
            assertFalse(reverseGeocoded, "the place already carries a country code; no reverse geocode needed")
        }

    @Test
    fun `a legacy selected place without country code is reverse-geocoded`() =
        runTest {
            val legacy = Place("Legacy", Coordinates(19.0, 72.8))
            val code =
                defaultCountryCode(
                    place = legacy,
                    deviceCoordinates = { null },
                    reverseGeocode = { coords -> if (coords == legacy.coordinates) "IN" else null },
                )
            assertEquals("IN", code)
        }

    @Test
    fun `when the device reverse geocode fails the selected place is the fallback`() =
        runTest {
            val code =
                defaultCountryCode(
                    place = mumbai,
                    deviceCoordinates = { Coordinates(52.3, 4.9) },
                    reverseGeocode = { coords -> if (coords.latitude == 52.3) null else "IN" },
                )
            assertEquals("IN", code)
        }

    @Test
    fun `unknown everywhere resolves to null`() =
        runTest {
            assertNull(defaultCountryCode(null, deviceCoordinates = { null }, reverseGeocode = { null }))
        }

    // --- grid sprinkling ---

    private fun piece(id: Int) =
        ClothingPiece(
            id = id.toLong(),
            name = "item$id",
            typeId = 1,
            notes = null,
            imagePath = "",
            thumbnailPath = "",
        )

    private fun sponsored(count: Int) = (1..count).map { item("Sponsored $it", "https://www.flipkart.com/s$it") }

    private fun sponsoredPositions(merged: List<WardrobeGridEntry>) =
        merged.withIndex().filter { it.value is WardrobeGridEntry.SponsoredItem }.map { it.index + 1 }

    @Test
    fun `sponsored count is wardrobe divided by four clamped to 1-5 plus one for an odd total`() {
        // 30 items -> base 5, total 35 odd -> 6 sponsored (hard cap), total 36 even.
        val big = mergeSponsoredIntoGrid((1..30).map { piece(it) }, sponsored(6), Random(42))
        assertEquals(6, big.count { it is WardrobeGridEntry.SponsoredItem })
        assertEquals(36, big.size)
        // 8 items -> base 2, total 10 even -> 2 sponsored.
        val eight = mergeSponsoredIntoGrid((1..8).map { piece(it) }, sponsored(6), Random(42))
        assertEquals(2, eight.count { it is WardrobeGridEntry.SponsoredItem })
        // 2 items -> base 1, total 3 odd -> 2 sponsored.
        val two = mergeSponsoredIntoGrid((1..2).map { piece(it) }, sponsored(4), Random(42))
        assertEquals(2, two.count { it is WardrobeGridEntry.SponsoredItem })
        // 1 item -> base 1, total 2 even -> 1 sponsored.
        val one = mergeSponsoredIntoGrid(listOf(piece(1)), sponsored(4), Random(42))
        assertEquals(1, one.count { it is WardrobeGridEntry.SponsoredItem })
    }

    @Test
    fun `sponsored cards are randomly sprinkled at distinct positions and user items keep their order`() {
        val userItems = (1..10).map { piece(it) }
        val merged = mergeSponsoredIntoGrid(userItems, sponsored(6), Random(7))
        val positions = sponsoredPositions(merged)
        // 10 items -> base 2, total 12 even -> 2 sponsored.
        assertEquals(2, positions.size)
        assertEquals(positions.distinct().size, positions.size, "positions must be distinct")
        assertTrue(positions.all { it in 1..merged.size })
        assertEquals(
            (1..10).map { it.toLong() },
            merged.filterIsInstance<WardrobeGridEntry.UserItem>().map { it.piece.id },
        )
    }

    @Test
    fun `fewer sponsored products than the target simply shows fewer`() {
        val merged = mergeSponsoredIntoGrid((1..30).map { piece(it) }, sponsored(2), Random(42))
        assertEquals(2, merged.count { it is WardrobeGridEntry.SponsoredItem })
    }

    @Test
    fun `grid skips sponsored cards when the filter leaves none`() {
        val userItems = (1..10).map { piece(it) }
        val merged = mergeSponsoredIntoGrid(userItems, emptyList(), Random(42))
        assertEquals(10, merged.size)
        assertTrue(merged.all { it is WardrobeGridEntry.UserItem })
    }

    @Test
    fun `an empty wardrobe still shows sponsored cards`() {
        val merged = mergeSponsoredIntoGrid(emptyList(), sponsored(4), Random(42))
        // 0 items -> base 1, total 1 odd -> 2 sponsored.
        assertEquals(2, merged.count { it is WardrobeGridEntry.SponsoredItem })
    }
}
