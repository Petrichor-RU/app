# Petrichor: Location Guide (year-round dress advice per location)

Implementation plan for the Location Guide feature on branch `dev`. Written to be executed by an  
agentic developer, in the same style as `2026-11-12-IMPLEMENTATION_PLAN.md`. Read together with  
`AGENTS.md` (binding rules).

All product decisions below were confirmed with the product owner on 2026-11-26. "MUST" statements  
are binding.

---

## 1. Summary

When a location is selected on the Weather tab, an expandable **"Location guide"** card appears  
below the 5-day forecast. Collapsed, it shows only its header. Expanded, it shows:

1. A **monthly strip**: average weather conditions for each of the 12 months.
2. One **season card** per meteorological season (Winter, Spring, Summer, Autumn), each listing  
generated dress advice ("Wear layered clothing", "Take a thick jacket with you", ...).

Advice is **generated from climate data**, never hand-written per location. The same clothing  
vocabulary and warmth knowledge as the recommendation engine is reused, so the guide and the  
Suggestions tab never contradict each other.

## 2. Confirmed decisions


| Decision                          | Choice                                                                                |
| --------------------------------- | ------------------------------------------------------------------------------------- |
| Placement                         | Weather tab, below the forecast, inside the existing content column                   |
| Default state                     | Folded in; expanding is a single tap on the header                                    |
| Monthly visualisation             | Compact strip: 12 bars showing temperature range + rain                               |
| Layout inside the guide           | Monthly strip on top, one card per season below                                       |
| Advice presentation               | Simple icon + sentence rows, grouped by season, all that apply shown                  |
| Scope                             | Any selected location                                                                 |
| Expansion persistence             | Stays expanded when switching tabs and returning                                      |
| Season grouping                   | Meteorological: Winter Dec–Feb, Spring Mar–May, Summer Jun–Aug, Autumn Sep–Nov        |
| Climate data source               | Open-Meteo Climate API (free, no API key)                                             |
| Caching                           | None; data is fetched when the guide needs it                                         |
| "Today" advice from live forecast | Not included; year-round data only                                                    |
| Loading / error behaviour         | The card is hidden entirely until data has arrived; on failure it simply stays hidden |


## 3. Data layer (`shared/src/data/guide/`)

### 3.1 `ClimateModels.kt`

```kotlin
data class MonthClimate(
    val month: Int,              // 1..12, constants naming below
    val avgHigh: Double,         // °C
    val avgLow: Double,          // °C
    val precipitationMm: Double, // total for the month
    val humidity: Int,           // %
)

data class ClimateProfile(
    val place: Place,            // reused from data/weather
    val months: List<MonthClimate>, // exactly 12 entries
)
```

No `expect`/`actual` declarations are needed: the existing `weatherHttpClient()` from  
`data/weather/WeatherClient.kt` is reused for the HTTP client.

### 3.2 `ClimateClient.kt`

Fetches from the Open-Meteo Climate API:

```
https://climate-api.open-meteo.com/v1/climate
    ?latitude={lat}
    &longitude={lon}
    &start_date=1990-01-01
    &end_date=2020-12-31
    &models=era5
    &daily=temperature_2m_max,temperature_2m_min,precipitation_sum
```

The client aggregates the daily series into 12 monthly normals in a **pure function** (separate  
from the HTTP call so it can be unit-tested with fixtures):

- average of all `temperature_2m_max` values in each calendar month → `avgHigh`
- average of all `temperature_2m_min` values → `avgLow`
- sum of `precipitation_sum` per calendar month, divided by the number of years → `precipitationMm`
- humidity is not offered by the climate API; derive `humidity` from the Weather tab's live  
snapshot when available, else `0` (rules that need humidity degrade gracefully, see 3.4)

Serialisation follows the existing `kotlinx.serialization` + `HttpClient` pattern in  
`WeatherRepository.kt`. Errors are surfaced as a nullable error string, mirroring  
`AppWeatherState`'s style.

### 3.3 `AdviceKind.kt`

Advice is an enum, not a string. Each value has exactly one English template (British English,  
consistent with the codebase; no i18n for now):

```kotlin
enum class AdviceKind(val template: String) {
    LAYERED_CLOTHING("Wear layered clothing so you can adjust through the day."),
    THICK_JACKET("Take a thick jacket with you."),
    THERMAL_BASE_LAYER("A thermal base layer is worth packing."),
    LIGHT_JACKET("A light jacket is usually enough."),
    WARM_ACCESSORIES("Bring a hat, scarf and gloves."),
    RAIN_GEAR("Keep a rain jacket or compact umbrella at hand."),
    WATERPROOF_FOOTWEAR("Waterproof footwear pays off this time of year."),
    BREATHABLE_FABRICS("Stick to breathable fabrics such as linen or cotton."),
    SUN_PROTECTION("Sunscreen and a cap are recommended."),
    COLD_SNAP_RISK("Be prepared for occasional cold snaps."),
    HEAT_SPIKE_RISK("Be prepared for occasional heat spikes."),
    WINDPROOF_LAYER("A windproof outer layer helps."),
}
```

Each kind maps to a Material Symbol icon (e.g. `LAYERED_CLOTHING` → `LAYERS`, `THICK_JACKET` →  
`CHECKROOM`/`AC_UNIT` as appropriate) chosen from the Material Symbols set already used app-wide.

### 3.4 `GuideEngine.kt`

A pure object, no I/O, no Compose imports:

```kotlin
fun seasonAdvice(profile: ClimateProfile, season: Season): List<AdviceKind>
```

`Season` is an enum (`WINTER`, `SPRING`, `SUMMER`, `AUTUMN`) with its three member months  
(Winter = 12, 1, 2; Spring = 3, 4, 5; Summer = 6, 7, 8; Autumn = 9, 10, 11).

Rules operate on the season's monthly normals (aggregating per-month values into per-season  
min/max/mean, rain totals, and the within-season spread). Thresholds are chosen so the recommended  
clothing matches what the recommendation engine would score highest for those temperatures  
(consistent with `WarmthEncoding`: jacket 1.0, hoodie 0.85, sweater 0.75, t-shirt 0.30, linen 0.20):

- Season mean high &lt; 5 → `THERMAL_BASE_LAYER`, `THICK_JACKET`, `WARM_ACCESSORIES`
- Season mean high in 5–12 → `LIGHT_JACKET` (plus `THICK_JACKET` if mean low &lt; 0)
- Season mean high &gt; 26 → `BREATHABLE_FABRICS`; &gt; 30 adds `SUN_PROTECTION`
- Within-season daily range (mean high − mean low) &gt; 8 → `LAYERED_CLOTHING`
- Mean monthly precipitation &gt; 120 mm → `RAIN_GEAR`; &gt; 200 mm adds `WATERPROOF_FOOTWEAR`
- Any single month in the season deviating &gt; 6 °C from the season mean high → the matching  
`COLD_SNAP_RISK` / `HEAT_SPIKE_RISK`
- Known humidity &gt; 70 % combined with mean high &gt; 26 → `BREATHABLE_FABRICS` (already implied by  
the temperature rule; humidity only acts as a tiebreaker when the temperature rule is borderline)

All rules that apply are returned, ordered by a fixed severity ranking (coldest-weather items  
first). No truncation. The thresholds live in this one file on purpose, as tunable constants.

### 3.5 `GuideRepository.kt`

Business boundary, mirroring `WeatherRepository`: `suspend fun climateFor(place: Place): ClimateProfile?`. Holds no cache by decision; a new selected place triggers a new fetch. Fetches  
are dispatched off the UI thread as elsewhere in the app.

## 4. UI layer (`shared/src/ui/weather/`)

### 4.1 `LocationGuide.kt`

One new composable, placed in `WeatherContent` below the 5-day forecast row, inside the existing  
`widthIn(max = forecastWidth)` column so it aligns with the cards above.

**Collapsed state:** a single `Card` with a **transparent container colour**  
(`containerColor = Color.Transparent`, no elevation overlay) holding the header row  
`MaterialSymbol(TRAVEL_EXPLORE)` + `"Location guide"` + an `EXPAND_MORE` arrow that rotates to  
`EXPAND_LESS` when expanded. Tap toggles expansion; the whole header row is the touch target.  
The header keeps this transparent background in both collapsed and expanded states.

**Expanded contents, in order:**

1. **Monthly strip.** A `LazyRow` (so it scrolls on narrow screens) of 12 compact items, visually  
echoing the rest of the app's style. The items carry **no**  
**background of their own**: the month label, range bar and temperatures render directly on the  
guide's transparent surface.
  - Month abbreviation (`Jan`...`Dec`, British English), `FontWeight.SemiBold`
  - A vertical range bar rendered as a `Box` gradient from `avgLow` to `avgHigh`, positioned  
  against a fixed global scale (−30 °C to +45 °C) so all locations are comparable
  - `avgHigh°` in `titleLarge`, `avgLow°` in `onSurfaceVariant` (same pattern as forecast cards)
  - A small rain indicator: `WATER_DROP` symbol plus the monthly mm value when &gt; 60 mm
2. **Season cards.** Four `Card`s in a column (Winter → Autumn order). These are the **only  \\n components with a background colour**, and each is lightly themed to its season:
  - `WINTER` → faint cool blue tint, `SPRING` → faint green tint, `SUMMER` → faint warm amber  \\n   tint, `AUTUMN` → faint orange tint
  - The tint is a translucent seasonal hue composited over `MaterialTheme.colorScheme.surface`  \\n   (seasonal `Color` at low alpha, roughly 0.08–0.12, composited onto the same 0.82–0.86 alpha  \\n   base as the forecast cards), so the cards stay legible and adapt to the light/dark park  \\n   schemes instead of fighting them
  - Header: season name (`titleMedium`, `FontWeight.SemiBold`) plus the season's mean  
   temperature range as a subdued suffix (e.g. "Winter • 6° / 1°")
  - Advice rows: one `Row` per `AdviceKind`, each a `MaterialSymbol` (tinted  
  `colorScheme.primary`) at 20 dp, 8 dp spacer, then the template sentence in `bodyMedium`.  
  Rows appear without individual fold-outs: everything shows at once when the guide opens.

**Theming (binding):** no opaque custom colours anywhere. The outer guide card and the header are  \\n`Color.Transparent`; the monthly strip items have no container; only the four season cards carry a  \\nbackground, defined as a light seasonal tint over `MaterialTheme.colorScheme.surface` with the  \\nsame alpha treatment as the existing forecast cards, so they sit consistently on the animated  \\npark background in both light and dark schemes. The seasonal tint constants live in one place  \\nin `LocationGuide.kt`. Typography comes from `MaterialTheme.typography` only. Icons use the  \\napp-wide `MaterialSymbolsRenderingScope` in `App.kt`; the guide MUST NOT create its own rendering  \\nscope.

### 4.2 State and animation rules

- Expansion state lives in `WeatherContent`'s composition as `remember { mutableStateOf(false) }`  
keyed by the selected place, so it **survives tab switches** (pager pages are retained), per  
the AGENTS.md clothing-detail-screen convention.
- The **opening animation plays only when opening** and the **closing animation only when**  
**closing**: use `AnimatedVisibility` with `expandVertically() + fadeIn()` enter and  
`shrinkVertically() + fadeOut()` exit, per the binding AGENTS.md rules.
- The climate fetch is triggered by a `LaunchedEffect(place)` when the guide is first expanded for  
that place; the composable renders `null` (card hidden entirely) until  
`GuideRepository.climateFor` returns. On error it also stays hidden; there is no error text, no  
spinner, and no retry button inside the guide. Switching to a new place resets to collapsed.
- While the place itself is still loading (`AppWeatherState.loading`), the whole Weather tab  
already shows the existing spinner, so the guide needs no separate loading UI.

## 5. Tests

Following the AGENTS.md conventions (repositories tested with fakes, no platform dependencies):

- `shared/test/data/guide/GuideEngineTest.kt`: pure rule tests against synthetic  
`ClimateProfile`s covering at least: tropical (hot/humid), maritime (mild, wet, layered-range),  
continental cold (Lisbon vs. Helsinki vs. Singapore style profiles), desert (spiky range), and  
boundary cases at each threshold edge. Assert emitted `AdviceKind` sets and their severity  
order.
- `shared/test/data/guide/MonthlyAggregationTest.kt`: fixture daily series → monthly normals.
- `shared/test/data/guide/GuideRepositoryTest.kt`: fake HTTP client, asserting success mapping and  
silent-null error behaviour.

No UI tests are required for the first pass; animation timing and layout are visual concerns,  
consistent with the wardrobe feature's stance.

## 6. Implementation order

1. `ClimateModels.kt` + `ClimateClient.kt` (with aggregation function) + tests
2. `AdviceKind.kt` + `GuideEngine.kt` + tests
3. `GuideRepository.kt` + test
4. `LocationGuide.kt` UI, wired into `WeatherContent`

Each step leaves the app in a working state; steps 1–3 have no UI footprint.

## 7. Out of scope (explicitly)

- Caching of climate data between loads
- "Today" advice derived from the live forecast
- Per-advice fold-outs or any second-level expansion
- Internationalisation of templates
- Any new `expect`/`actual` declarations
- Changes to the location picker, saved locations, or the shared weather load