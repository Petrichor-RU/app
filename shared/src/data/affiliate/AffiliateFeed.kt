package nl.petrichor.app.data.affiliate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import nl.petrichor.app.data.affiliate.adapters.FlipkartAdapter
import nl.petrichor.app.data.location.LocationSearchRepository
import nl.petrichor.app.data.location.currentCoordinates
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Hard cap on sponsored cards fetched/shown at once. The grid rule is
 * `coerce(wardrobeCount / 4, 1, 5)`, plus one more when that total would be odd — so 6 is the
 * hard maximum (see ui/wardrobe/SponsoredGrid.kt).
 */
const val MAX_AFFILIATE_ITEMS = 6

/** Timeout for the store fetch itself: a slow store must never hang the wardrobe. */
private val FETCH_TIMEOUT = 20.seconds

/**
 * Timeout for country resolution (device geolocation self-caps at 15s, then a reverse-geocode
 * HTTP call). Kept separate from [FETCH_TIMEOUT] so a slow fix cannot starve the store fetch.
 */
private val RESOLVE_TIMEOUT = 30.seconds

/** How long a fetched advert list stays valid; after that the next refresh fetches anew. */
private val CACHE_TTL = 1.hours

/**
 * Feed engine: resolves the user's country (real device location first, the selected weather
 * place as fallback), picks the store serving that country (none → no sponsored items at all),
 * derives the shopping context from the *selected location's* forecast and runs that store's
 * adapter once, capped at [maxItems]. Any failure (blocked request, no network, no results,
 * CORS on WASM) degrades silently to an empty list.
 *
 * Results are cached in memory (never persisted) under a key of selected place + resolved
 * country and reused for about an hour, so the same adverts are shown until the user switches
 * location or the cache expires. [fetch] calls [onCacheMiss] whenever it does not serve from
 * the cache, so callers can clear the stale adverts before the fresh fetch completes.
 *
 * Fetches are serialized through a mutex: the app shell pre-warms the same feed at startup, and
 * a concurrent second fetch for the same key would both double-hit the store (risking a
 * bot-blocked empty response) and race the cache write — the loser could cache an empty list for
 * an hour. With the mutex, the second caller waits and then serves from the cache. Empty adapter
 * results are likewise never cached (they may be transient blocks); only the deterministic
 * "country not served" empty and non-empty results are.
 */
class AffiliateFeed(
    private val adapter: AffiliateAdapter = FlipkartAdapter(),
    private val maxItems: Int = MAX_AFFILIATE_ITEMS,
    private val resolveCountryCode: suspend (Place?) -> String? = { place -> defaultCountryCode(place) },
    private val now: () -> Instant = { Clock.System.now() },
    private val cacheTtl: Duration = CACHE_TTL,
) {
    private data class CacheEntry(
        val key: String,
        val fetchedAt: Instant,
        val items: List<AffiliateItem>,
    )

    private var cache: CacheEntry? = null
    private val fetchMutex = Mutex()

    /**
     * Fetch for [place] (the selected weather location) and [weather] (its snapshot), served
     * from the cache when the key matches and the entry is younger than [cacheTtl].
     * Never throws except cancellation; failures degrade to an empty list (and are not cached,
     * so the next refresh retries) so the wardrobe always renders.
     */
    suspend fun fetch(
        place: Place?,
        weather: WeatherSnapshot?,
        onCacheMiss: () -> Unit = {},
    ): List<AffiliateItem> =
        fetchMutex.withLock {
            try {
                val countryCode = withTimeout(RESOLVE_TIMEOUT) { resolveCountryCode(place) }
                val key = cacheKey(place, countryCode)
                val cached = cache
                if (cached != null && cached.key == key && now() - cached.fetchedAt < cacheTtl) {
                    return@withLock cached.items
                }
                onCacheMiss()
                val store = AffiliateStore.entries.firstOrNull { countryCode != null && countryCode in it.supportedCountries }
                val items =
                    if (store == null) {
                        emptyList()
                    } else {
                        withTimeout(FETCH_TIMEOUT) {
                            adapter
                                .fetch(deriveShoppingContext(weather), maxItems)
                                .distinctBy { it.url }
                                .take(maxItems)
                        }
                    }
                // An empty adapter result may be a transient block — never cache it.
                if (store == null || items.isNotEmpty()) {
                    cache = CacheEntry(key, now(), items)
                }
                items
            } catch (_: TimeoutCancellationException) {
                emptyList()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                emptyList()
            }
        }

    private fun cacheKey(
        place: Place?,
        countryCode: String?,
    ): String =
        "${place?.name ?: "current-location"}|${place?.coordinates?.latitude},${place?.coordinates?.longitude}|" +
            "${place?.countryCode ?: "-"}|${countryCode ?: "-"}"
}

/**
 * The user's country for store selection: the real device location when geolocation is
 * available and reverse-geocodable, otherwise the selected [place]'s own country code (or a
 * reverse geocode of its coordinates for legacy saved locations). Null when neither resolves.
 */
internal suspend fun defaultCountryCode(
    place: Place?,
    deviceCoordinates: suspend () -> Coordinates? = ::currentCoordinates,
    reverseGeocode: suspend (Coordinates) -> String? = { coords -> sharedSearchRepository.countryCodeFor(coords).getOrNull() },
): String? {
    // Each step is isolated so a device/geocode failure still falls back to the selected place.
    val device =
        runCatching { deviceCoordinates() }
            .onFailure {
                if (it is CancellationException) {
                    currentCoroutineContext().ensureActive()
                }
            }.getOrNull()
    val fromDevice =
        if (device != null) {
            runCatching { reverseGeocode(device) }
                .onFailure {
                    if (it is CancellationException) {
                        currentCoroutineContext().ensureActive()
                    }
                }.getOrNull()
        } else {
            null
        }
    return fromDevice
        ?: place?.let { selected ->
            selected.countryCode ?: runCatching { reverseGeocode(selected.coordinates) }
                .onFailure {
                    if (it is CancellationException) {
                        currentCoroutineContext().ensureActive()
                    }
                }.getOrNull()
        }
}

/** One shared repository for all reverse-geocoding lookups, so each fetch does not spin up a new HTTP client. */
private val sharedSearchRepository: LocationSearchRepository by lazy { LocationSearchRepository() }
