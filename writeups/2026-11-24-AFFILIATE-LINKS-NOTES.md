# Affiliate links — companion notes (manual verification)

> **Scope update (later revision):** only **Flipkart** works reliably, so the Amazon/Myntra/Vinted
> adapters, the region-based multi-store merge, and the non-Flipkart `AppConfig.AffiliateCodes`
> entries were removed. The feed is now a single `FlipkartAdapter` (Affiliate API first, scraper
> fallback); the affiliate code is injected by the **adapter itself** — `FlipkartAdapter` owns
> the Flipkart-specific tagging (`flipkartAffiliateLink` appends only `?affid=<flipkartCode>`,
> idempotent; no shared `AffiliateConfig` remains) and tags every product URL before building
> the item, so `AffiliateItem.url` is a ready-to-go affiliate link and click handlers open it
> as-is; any future adapter injects its own store-specific code the same way. Fetched adverts
> are cached **in memory** (never persisted) under a key of selected place + resolved country
> for ~1 h — the same adverts show until the user switches location or the TTL expires, at which
> point the grid is cleared and refetched. One shared `AffiliateFeed` is owned by the app shell
> and pre-warmed on app load (once the weather snapshot exists), so opening the Wardrobe tab
> usually hits the cache. Sponsored items are still
> **country-gated**: the user's country is resolved from the real device location first (reverse
> geocoding), falling back to the selected location, and only countries in
> `AffiliateStore.supportedCountries` (Flipkart = IN) see sponsored cards; the shopping context
> still comes from the *selected* location's weather. Grid rule: `wardrobe/4` sponsored cards,
> clamped to 1..5, +1 when the total would be odd (hard cap 6), randomly sprinkled. The sections
> below are kept as the historical record of the research and the removed adapters.

Companion notes for `2026-11-24-AFFILIATE-LINKS.md` §4: per-store manual checklists, recorded as each
adapter landed. Scraping internals have no unit tests by decision; the API response parsers have
unit tests (`shared/test/data/affiliate/`). Verify manually per store and on all four targets
(Android, Desktop, iOS, WASM).

## Public APIs (researched 2026-11-24) — scraping is only a fallback

| Store | Public API | Affiliate link | Status |
|---|---|---|---|
| Amazon | **Creators API** (PA-API 5.0 retired May 2026): OAuth2 client-credentials token from regional LwA endpoint (`api.amazon.com` / `api.amazon.co.uk` / `api.amazon.co.jp/auth/o2/token`), then `POST https://creatorsapi.amazon/catalog/v1/searchItems` with `x-marketplace` header + `marketplace`/`partnerTag` in the body | `detailPageURL` comes back with `?tag=<partnerTag>` automatically; `decorate()` adds it as backstop | Endpoints live (400 on unsigned probe). Needs `amazonClientId`/`amazonClientSecret` + per-country `amazonTags` in AppConfig |
| Flipkart | **Affiliate API**: `GET https://affiliate-api.flipkart.net/affiliate/1.0/search.json?query=<q>&resultCount=<n>` with headers `Fk-Affiliate-Id` / `Fk-Affiliate-Token` | Product URLs get the tracking id appended as `?affid=<id>` (docs: append it to feed URLs); `decorate()` adds `affid` | Live (401 "Invalid Headers" with dummy creds). Needs `flipkartCode` (= tracking id) + `flipkartAffiliateToken` |
| Myntra | **None for public catalog search** (`apidocs.myntra.com` is the Seller API); affiliate programme runs via CueLinks deep links | `affid`-style param via `decorate()` hook | Scraper remains the discovery path |
| Vinted | **No official public API** (only third-party scrapers) | `affid`-style param via `decorate()` hook | Server-rendered catalog page is the public data surface; scraper remains |

Adapters are **API-first**: Amazon and Flipkart use the official API when credentials are configured
in `AppConfig.AffiliateCodes` and fall back to the HTML scrapers otherwise (or stay silent when
blocked). With all codes empty (default) links stay plain product URLs, per decision 13.
`decorate()` is idempotent: it never appends a second `tag=`/`affid=` to links that already carry one.

## Sandbox probe results (2026-11-24, this environment's IP, browser User-Agent)

| Probe | Result |
|---|---|
| `POST https://creatorsapi.amazon/catalog/v1/searchItems` `{}` | 400 (endpoint live, needs bearer token) |
| `POST https://api.amazon.{com,co.uk,co.jp}/auth/o2/token` `{}` | 400 on all three (live) |
| `GET https://affiliate-api.flipkart.net/affiliate/1.0/search.json?query=hoodie` with dummy headers | 401 "Invalid Headers" (endpoint live) |
| `GET https://www.flipkart.com/search?q=hoodie&marketplace=FLIPKART` | HTTP 200 (~1.2 MB); `"productInfo":` blocks parse — verified offline: 40 blocks with `baseUrl`, `titles.newTitle`, `pricing.prices[Selling Price]`, `media.images[0].url` (templated `{@width}/{@height}/{@quality}`) |
| `GET https://www.vinted.com/catalog?search_text=hoodie&per_page=24` | HTTP 200 (~3.6 MB); items in the Next.js RSC payload `\"items\":{\"items\":[` — verified offline: 96 items with `productItem.title/url/price/photos[0].url` |
| `GET https://www.amazon.com/s?k=hoodie` | 503 (bot protection from datacenter IPs) → silent fallback as designed |
| `GET https://www.myntra.com/hoodies` | Connection refused (Akamai) from this IP → silent fallback as designed |

The Vinted adapter's parsing was validated offline against a recorded catalog page; the Flipkart
scraper's marker was corrected after offline verification (`"productInfo":` — must end before the
object's `{` so the balanced extractor grabs the whole block, not the inner `tracking` object).

## Per-store manual checklist (run on a real device/network, per store)

For each store: pick a location in the store's region, open the Wardrobe tab, confirm 2–4 sponsored
cards appear at grid positions ~4/12/20/28 (or filling the grid beneath the message on an empty
wardrobe), tap one, confirm the detail screen shows store/price/type (photo left, details right at
>=600 dp, like a wardrobe item) and "Shop at <Store>" opens the product page externally —
with the plain URL when codes are empty, with `affid=` once the code is filled in (injected by
the adapter when the item is built, so the tapped link is already tagged).

- [ ] **Amazon** (`AmazonAdapter` + `AmazonCreatorsApi`): with `amazonClientId`/`amazonClientSecret`/
      `amazonTags["IN"]` set, an Indian location returns items whose detail URLs contain `?tag=`.
      Without credentials it falls back to scraping `https://www.<domain>/s?k=<term>`
      (`amazonDomain(countryCode)` map, fallback `amazon.com`), parsing
      `data-component-type="s-search-result"` blocks — expect 503/captcha on many networks.
- [ ] **Flipkart** (`FlipkartAdapter` + `FlipkartAffiliateApi`): with `flipkartCode`/`flipkartAffiliateToken`
      set, an Indian location returns items from the Affiliate API search. Without credentials it
      scrapes `https://www.flipkart.com/search?q=<term>&marketplace=FLIPKART` (`"productInfo":` blocks).
- [ ] **Vinted** (`VintedAdapter`): `https://www.<vinted-domain>/catalog?search_text=<term>`
      (`vintedDomain(countryCode)` map, fallback `vinted.com`); parses the RSC-embedded
      `\"items\":{\"items\":[` array. Verify on a European location (e.g. NL/DE).
- [ ] **Myntra** (`MyntraAdapter`): `https://www.myntra.com/<category-path>` (`typeNameToPath` map);
      parses the embedded `"products":[` array (`productName`, `landingPageUrl`/`productUrl`, `price`,
      `images[0].src`/`styleImages.default.image`). Akamai-blocked from datacenters — verify on a
      real Indian network.

## Cross-cutting checks

- [ ] Any location → Flipkart items at grid slots 4/12/20/28 (clamped/appended for wardrobes
      with fewer than 4 items); region/country-based store selection was removed with the other
      adapters.
- [ ] Forecast-aware: cold/rainy forecast surfaces jackets/pants; warm dry surfaces tees/shorts.
- [ ] Type filter filters sponsored cards like user items; empty result skips the slots.
- [ ] Empty wardrobe → message + sponsored cards in the same adaptive grid beneath it
      (two per row on phones, more when wide, like wardrobe items).
- [ ] No network / store blocked → wardrobe renders normally with zero sponsored cards (silent).
- [ ] WASM target: feed/images usually absent due to CORS — accepted (writeup §6).
- [ ] No tracking of any kind; affiliate codes empty by default → plain product URLs.
- [ ] `./kotlin build`, `./kotlin test`, `tools/lint.sh` all clean.
