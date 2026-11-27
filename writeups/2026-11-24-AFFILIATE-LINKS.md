# Petrichor: Affiliate clothing items

Implementation plan for affiliate clothing items: clearly marked, location-based "Sponsored" products in the wardrobe grid that link out to Myntra, Flipkart, Amazon and Vinted. Written to be executed by an agentic AI developer. Read together with APP\_OVERVIEW.md, AGENTS.md and the existing wardrobe code in `shared/src/ui/wardrobe/`.

Last decision refresh: 2026-11-24.

## 0. How to use this document

- "MUST"/"always"/"never" statements are binding. "Should" statements are strong defaults; deviate only with a comment explaining why.
- All decisions in section 1 were confirmed by the owner in an interview (2026-11-24). Do not re-litigate them.
- The executing agent is responsible for discovering, per store, the concrete endpoints or page structures to read, and for all adapter code. This plan specifies the contract, not scrapers' internals, because store internals can change between now and execution.
- Stick to the instructions in AGENTS.md

## 1. Product decisions (authoritative)

1. Affiliate items appear **mixed into the wardrobe grid**, not on a separate tab. The Suggestions tab is untouched by v1.
2. They are clearly marked with an orange **"Sponsored" badge** on the card. No separate store branding on the card; the store is revealed on the detail screen.
3. Quantity: **2-4 items** visible at a time. **Sprinkling rule**: first affiliate item at grid position \~4, then one approximately every 8 positions, capped at 4.
4. **Forecast-aware relevance**: shopping decisions use the full **5-day forecast** of the selected weather location, and match **temperature and rain** (a wet or cold forecast yields rain-appropriate or warm items; a warm dry one yields e.g. t-shirts).
5. Tapping a card opens an **in-app detail screen first** (image, store, price, description/reviews when available), with a **"Shop at &lt;Store&gt;"** button that opens the product page in the **external browser**.
6. **Price is shown on the detail screen only**, never on the grid card.
7. **Location-based store selection** derives from the **selected weather location** (not device locale, not Settings): India → **Myntra and Flipkart (mixed)**, Europe → **Vinted**, rest of the world → **Amazon**.
8. When the user filters the wardrobe by clothing type, affiliate items are **filtered like the user's own items** (each affiliate item carries a mapped `ClothingType`/supertype).
9. Empty wardrobe: the existing empty-wardrobe message stays, with the affiliate items as a **strip beneath it**.
10. **Fresh fetch on every app launch** (`WardrobeScreen` first composition); nothing is persisted. On any failure (blocked request, no network, no results): **silent fallback, nothing is shown**.
11. **No tracking of any kind**.
12. **All four adapters ship in v1.**
13. **Affiliate codes live in `AppConfig.kt`** (git-ignored): built now, empty by default, so links stay plain product URLs until codes are filled in. **Amazon requires one affiliate code per country/marketplace** (each Amazon marketplace has its own Associates tag); `AppConfig` therefore holds a per-country map for Amazon plus separate fields per other store.
14. Accepted risk: the app **scrapes the store sites directly**, in-process, with no backend. Adapters MUST degrade silently (decision 10) when a store blocks us or a platform (notably WASM, CORS) cannot reach the store.

## 2. Feature architecture

Affiliate items are **never Room entities** and never enter `WardrobeRepository`. They exist only in the `WardrobeViewModel`-adjacent affiliate state.

### 2.1 Region resolution

`AffiliateRegion.derive(countryCode: String?): List<AffiliateStore>`:

- India (`IN`) → `[MYNTRA, FLIPKART]`, items **mixed from both stores** across the 2-4 slots.
- Europe: the EEA/UK/EFTA country codes → `[VINTED]`.
- Everything else → `[AMAZON]`.
- Unknown/unresolvable country → `[AMAZON]` (Amazon is "worldwide").

The country comes from the **selected weather location**. The existing `Place`/OpenWeather types currently carry `name` and `coordinates`; a `countryCode` **MUST** be added to `Place` (the OpenWeather geocoding payload includes `country`/`country_code`; extract it in `LocationSearchRepository` and in the geolocation/fallback paths). Legacy saved locations without a code resolve via the geocoding API lazily, falling back to `[AMAZON]` when that fails.

### 2.2 Shopping context (forecast → what to shop for)

A single derived value, computed in common code:

```kotlin
data class ShoppingContext(
  rainExpected: Boolean,          // any of the 5 forecast days is rainy
  temperatureBand: TemperatureBand, // WARM, MILD, COOL, COLD
)

enum TemperatureBand { WARM, MILD, COOL, COLD }
```

Derivation, binding:

- `rainExpected = true` if any forecast day (including today) is rainy (existing rain detection: description contains "rain" or icon prefix `09`/`10`/`11`).
- The band is taken from the **coldest** daily minimum in the 5-day forecast alongside the current temperature's band; the colder of the two wins. This makes the feature forecast-aware: a cold snap three days out still surfaces warm layers, while an isolated warm day in winter does not surface swimwear-level suggestions.
- Default band mapping (bind as constants, so tuning is one-line): WARM ≥ 22 °C, MILD 15-21, COOL 8-14, COLD &lt; 8 day-min.

Target mapping to clothing (shared, drives adapter queries and post-filtering):


| Context               | Target types                                                     |
| --------------------- | ---------------------------------------------------------------- |
| COLD / COOL + no rain | Hoodies, Sweaters, Jackets                                       |
| COLD / COOL + rain    | Rain-appropriate: Jackets, long Pants, waterproof-style footwear |
| MILD                  | Shirts, light Sweaters                                           |
| WARM + no rain        | T-Shirts, Shorts                                                 |
| WARM + rain           | T-Shirts, Pants                                                  |
| Any                   | always allow accessories/headwear/footwear to fill unused slots  |


### 2.3 Adapters

- Adapters are **thick**: each adapter decides its own query strategy for its store (category pages, search endpoints), scrapes, and returns items already relevant. There is no shared post-filter besides type mapping.
- Each adapter maps its store's taxonomy onto the built-in clothing types and supertypes; an item that cannot be mapped MUST be dropped.
- Each adapter MUST return only `https://` links to product pages.
- Each adapter runs through its own Ktor client instance with a realistic browser `User-Agent`. We use the existing `expect weatherHttpClient()` pattern as a template but a separate `expect fun affiliateHttpClient(store: AffiliateStore): HttpClient`.
- Failures (HTTP error, empty/changed layout, timeout, CORS on WASM) are caught in `AffiliateFeed` and degrade silently (decision 10). Adapters MUST NOT themselves surface errors to the UI.

### 2.4 Feed engine

- Derive the region (2.3) and context (2.4).
- Allocate the 2-4 slots across the eligible stores: in India, alternate Myntra/Flipkart. In single-store regions, one adapter runs. Items that come back per store: dedupe by name similarity and by URL; cap the total at 4.
- An adapter failing MUST NOT block other stores; run adapters concurrently (`coroutineScope` + `async`), collect successes, ignore failures.

### 2.5 Affiliation (AppConfig)

`AppConfig.kt` gains a new nested object (git-ignored, `.template` updated first):

```kotlin
object AffiliateCodes {
  // Amazon: one affiliate tag per country/marketplace. Key: ISO-3166 alpha-2 of the selected location.
  val amazonTags: Map<String, String> = emptyMap()   // e.g. "IN" to "ourtag-21", "DE" to "ourtag-21", "NL" to "ourtag-21"
  val myntraCode: String? = null
  val flipkartCode: String? = null
  val vintedCode: String? = null
}
```

- Decoration happens in `AffiliateConfig.decorate(link, store, countryCode)` at click time. Empty/missing code → link returned unchanged (no-op), never a broken URL.
- Amazon MUST resolve its tag by the country of the selected location, and, when that country has no entry, falls back to an undecorated link.
- The exact query-parameter conventions (Amazon `tag=`, Flipkart `affid`, Myntra/Vinted: whichever their affiliate programme/network prescribes at signup) are to be confirmed when the programmes are joined; until then decoration stays a no-op with empty maps. The plan intentionally builds the hook now.

## 3. UI changes (all in `shared/src/ui/wardrobe/` and `ui/`)

### 3.1 Grid

- `WardrobeScreen` collects affiliate state; the `LazyVerticalGrid` gets `item(span)` rows/reordering so the merged sequence is: user items with affiliate cards inserted at positions 4, 12, 20, 28 with a hard cap of 4 affiliate cards total (position 4 = index 3).
- The type filter applies to affiliate cards through their `supertype`/`typeName` like normal cards (filter stays owned by `WardrobeScreen`).
- Empty wardrobe: existing empty-wardrobe message remains first and untouched; a strip of affiliate cards follows beneath it.
- If the filter produces zero affiliate items, positions are simply skipped; user items must never shift to make room for an affiliate card.

### 3.2 Card

- Reuse the `ClothingCard` visual language; new `SponsoredClothingCard`: square remote image (new `expect fun loadRemoteImagePainter(url)`), no name overlay in v1, and a orange "Sponsored" badge (top-start overlay, `MaterialTheme.colorScheme.surfaceVariant`-on-`surface` styling, compact corner tag).

### 3.3 Detail overlay

- New `AffiliateDetailScreen`, style-compatible with `ClothingDetailScreen` (hero image, close button); rows: Store, Price (when present), Type. Body: description when present.
- Primary action: filled button "Shop at &lt;Store&gt;" → `PlatformBrowser.open(url)` (expect/actual: Android Intent `ACTION_VIEW`, JVM `Desktop.browse`, iOS `UIApplication.shared.open`, wasmJs `window.open`). The URL passed is `decorate(link, store, countryCode)`.

### 3.4 Weather-staleness rule

- When weather fails to load or is stale (as defined by the background plan's fallback), the affiliate context MUST degrade to the neutral current-band default for "today" used by the weather tab (never crash, never block the wardrobe).

## 4. Testing

- **Scrapers: manual verification only**, no unit tests for scraping internals (decision from interview). Record per-store manual checklists in the writeup's companion notes as each adapter lands.

## 5. Implementation steps

1. Add `countryCode` to `Place` + propagate into the weather/location flows (also update fallback to include its country).
2. `data/affiliate` skeletons
3. UI plumbing: `SponsoredClothingCard`, sponsored-card grid insertion, empty-wardrobe strip; visible with plain fakes until adapters land.
4. `AffiliateDetailScreen`, `PlatformBrowser` expect/actuals, URL decoration (AppConfig template update first).
5. Amazon adapter (worldwide baseline).
6. Vinted adapter.
7. Myntra adapter.
8. Flipkart adapter (fetch flow in the previous step is reused).
9. Manual verification pass per store + on all four targets; detekt/ktlint clean; update APP\_OVERVIEW.md's feature table.

## 6. Accepted risks and future work

- In-app scraping is fragile by design (decision 14): layout changes or bot protection can silently empty the feed. A backend scraping proxy is the known escape hatch; revisit if two stores break simultaneously.
- WASM: cross-origin requests to store sites will usually be blocked by CORS; affiliate items will mostly be absent on the web target. Accepted.
- Future, out of scope: tapping analytics, affiliate network signups per marketplace, Suggestions integration, in-app WebView.