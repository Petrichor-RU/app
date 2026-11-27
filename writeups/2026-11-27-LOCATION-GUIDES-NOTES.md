# NOTES — Location Guide: amendments to the 2026-11-26 implementation plan

This document amends `2026-11-26-LOCATION-GUIDES.md`. It records where the **as-built**
Location Guide differs from that original proposal. Read it together with the original;
section numbers below refer to the original document. Where this NOTES document and the
original disagree, this document wins.

The feature shipped on the Weather tab as an expandable **"Location guide"** section below
the 5-day forecast: a monthly climate strip plus one advice card per meteorological season,
with a "General" card for advice that holds all year.

---

## Amendments by section

### §2 "Confirmed decisions" — loading / error behaviour (changed)

- **Original:** the card is hidden entirely until data has arrived; on failure it stays
  hidden (no error text, no spinner, no retry button).
- **As-built:** the guide header is **always visible** — it has no visibility switches of its
  own. While a new place's climate loads, the header chevron is replaced by a spinner and the
  **previous profile stays on screen** until the new one arrives. When a fetch fails, the
  profile is cleared and the expanded area shows an explicit message, *"No climate data
  available for this location yet."* (`LocationGuide.kt`). The forecast's own
  loading/error swap (`AnimatedContent` in `ForecastBody`) is unchanged, and the guide sits
  **outside** that swap so it never disappears while the forecast reloads.

### §2 — expansion persistence (changed)

- **Original:** stays expanded when switching tabs and returning (kept), but "switching to a
  new place resets to collapsed".
- **As-built:** the expansion flag is **not** reset on place switch. It lives in an
  app-shell-scoped `GuideViewModel` (see §4.2 below), so it also survives the location picker
  opening and the forecast's loading spinner. Verified by `GuidePlaceSwitchTest`.

### §3.2 `ClimateClient.kt` — API window and rate limiting (changed / added)

- **Original:** `start_date=1990-01-01`, `end_date=2020-12-31` (full 1990–2020 range).
- **As-built:** the window is shortened from the full 1990–2020 range to
  `start_date=2014-01-01` … `end_date=2020-12-31` — a **7-year window**, chosen deliberately
  to stay within the free tier's 10,000 calls/day budget (the full 1990–2020 range costs
  ~1,800 calls/request). The constants are named `CLIMATE_START_DATE` / `CLIMATE_END_DATE`.
  - **Note:** the `2014-01-01` start is intentional, not a typo. The `ClimateClient.kt`
    comment now describes this 7-year window, and `GuideRepositoryTest` no longer asserts a
    `start_date` value (it checks only latitude, longitude and `models=era5`).
- **Added (not in original):** HTTP 429 (free-tier rate limit) is retried up to 2 times,
  honouring `Retry-After` (capped at 60 s, default 30 s); cancellation is rethrown so
  switching places cancels the in-flight request; rate-limit headers are logged.

### §3.4 `GuideEngine.kt` — rules (mostly as proposed, with additions)

- Temperature, daily-range, rain and snap rules match the proposal (thresholds 5 / 12 / 0 /
  26 / 30 / 8 / 120 / 200 / 6 °C·mm as listed), all kept as tunable constants in the one file.
- **Added:** a humidity tiebreaker — when `maxHumidity > 70` and the season mean high is in
  the borderline zone (`> 24 °C`, `BORDERLINE_HOT_HIGH_C`) but below the hot threshold
  (`26 °C`), `BREATHABLE_FABRICS` is still emitted. Above 26 °C the temperature rule already
  implies it.
- **Added:** `generalAdvice(profile)` — the intersection of advice across all four seasons,
  rendered as the new "General" card; season cards do not repeat kinds already shown there.
- **Note:** `AdviceKind.WINDPROOF_LAYER` exists in the enum but no rule emits it yet.

### §3.5 `GuideRepository.kt` — signature (changed)

- **Original:** `suspend fun climateFor(place: Place): ClimateProfile?`
- **As-built:** `suspend fun climateFor(place: Place, currentHumidity: Int = 0): ClimateProfile?`
  The climate API offers no humidity, so the Weather tab's live snapshot humidity is passed in
  and attached to every month (0 = unknown; humidity rules degrade gracefully). Still no
  cache; fetches dispatched off the UI thread.

### §4.1 `LocationGuide.kt` — monthly strip (changed)

- **Original:** a horizontally scrollable `LazyRow` of 12 items, each with a `Jan`…`Dec`
  label, a range bar on a **fixed global −30…45 °C** scale, `avgHigh`/`avgLow` text, and a
  per-month rain indicator (`WATER_DROP` + mm when > 60 mm). Strip items had no background.
- **As-built** (`GuideMonthlyStrip.kt`): a **fixed, non-scrolling `Row` of 12 equal-weight
  bars** on a theme-following chart panel (surface background, 16 dp rounded corners). A left
  scale legend shows the **profile's own** max avgHigh / min avgLow; bars are scaled to the
  profile's own extremes (not a fixed global scale). Bar colour is mapped by **absolute**
  temperature (blue at −30 °C → red at 45 °C) at faint alpha, as a vertical hot→cold
  gradient. Only three month labels are shown — "January" bottom-left, "July" at the seventh
  bar, "December" right-aligned across the full row (overflowing left, never wrapping
  mid-word). **Rain moved off the graph** onto the season-card headers as a `RainDrops`
  indicator (`RainDrops.kt`): one drop per 50 mm, shown from 10 mm, capped at 5, with a
  "Rain, N mm" content description.

### §4.1 — card backgrounds and the General card (changed / added)

- The outer guide card and header remain `Color.Transparent` with zero elevation, as
  proposed. Season cards keep a seasonal tint over the surface base, now at alpha `0.10`
  over a `0.84` surface base (`SEASON_TINT_ALPHA` / `SEASON_CARD_BASE_ALPHA` in
  `LocationGuide.kt`), within the proposed 0.08–0.12 range.
- **Changed:** the monthly strip is now its own panel **with** a surface background (the
  original said strip items had no container).
- **Added:** a neutral "General" card (`GuideGeneralCard.kt`, surface at 0.84) above the
  season cards for year-round advice; season cards exclude those kinds.
- **Header** (`GuideHeader`): just the "Location guide" title plus a rotating
  `KEYBOARD_ARROW_DOWN` chevron (180° when expanded) or a spinner while loading — the
  original's `TRAVEL_EXPLORE` icon is not used.

### §4.2 — state and animation rules (changed)

- **Original:** expansion state in `WeatherContent`'s composition as
  `remember { mutableStateOf(false) }` keyed by place; fetch triggered by
  `LaunchedEffect(place)` on first expand; card hidden until data arrives.
- **As-built:** state lives in a new **app-shell-scoped `GuideViewModel`** owned by `App.kt`
  (mirroring `AppBackgroundController`), exposing `profile`, `loading` and `expanded`
  `StateFlow`s plus `ensureClimateLoaded(place, humidity)`. `WeatherScreen`/`ForecastBody`
  receive it and call `ensureClimateLoaded` from a `LaunchedEffect(weather?.place)`. Because
  it is shell-scoped, the profile, in-flight fetch and expansion flag survive tab switches
  and the picker opening, and a fetch is never cancelled with the page. A newer place
  request drops a stale in-flight result; only the latest request publishes/clears `loading`;
  a null result clears the profile (→ not-available message). The open/close animations are
  still `expandVertically() + fadeIn()` / `shrinkVertically() + fadeOut()` on the expandable
  content only, per the binding AGENTS.md rules.
- **Layout:** the `widthIn(max = forecastWidth)` cap and vertical scroll moved up from
  `WeatherContent` (now `fillMaxWidth()`) into `ForecastBody`, whose column is
  `widthIn(max = 580.dp).verticalScroll(...)` and hosts the forecast `AnimatedContent` plus
  the `LocationGuide`, so the whole tab scrolls on short windows.

### §5 Tests (changed)

- **Original:** "No UI tests are required for the first pass."
- **As-built:** JVM Compose UI tests were added — `LocationGuideTest`, `GuidePlaceSwitchTest`
  (plus a scroll test in `WeatherContentTest`) using `runDesktopComposeUiTest`, wrapped in
  `MaterialSymbolsRenderingScope` per the test-environment exception. Data tests
  (`GuideEngineTest`, `MonthlyAggregationTest`, `GuideRepositoryTest` with a Ktor
  `MockEngine`, and an added `GuideViewModelTest`) cover rules, aggregation, HTTP mapping and
  view-model semantics. UI tests assert user-visible behaviour and callbacks, not animation
  timing.

---

## Not changed from the original

Placement (Weather tab, below the forecast), meteorological season grouping (Winter Dec–Feb
…), Open-Meteo Climate API as the source, no caching, no "today" advice from the live
forecast, advice generated from climate data reusing the recommendation engine's warmth
vocabulary, no i18n (British English templates), no new `expect`/`actual`, and no changes to
the location picker / saved locations / shared weather load.
