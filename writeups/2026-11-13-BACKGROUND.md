# Petrichor: The Background (adaptive park scene)

Implementation plan for "the background": a park scene behind all app content that adapts to the time of day and the current weather, with a light/dark theme that follows the sky. Written to be executed by an agentic AI developer. Read together with `APP_OVERVIEW.md`, `AGENTS.md`, and the "Outfit Journal &amp; Smart Recommendations" implementation plan (referred to below as *the journal plan*; the background assumes its tab restructure, section 2.1, and its shared `SelectedLocationStore`, section 8).

Last decision refresh: 2026-11-13.

---

## 0. How to use this document

- "MUST" / "always" / "never" statements are binding. "Should" statements are strong defaults; deviate only with a comment explaining why.
- All decisions in section 1 were confirmed by the owner in an interview (2026-11-13). Do not re-litigate them.
- **The executing agent (or person) is responsible for creating the designs.** All artwork for the park scene, all palettes, and all visual details described here are to be authored by the implementer as part of this work. No external asset files are supplied with this plan, and none are required: the production scene is drawn in code (section 5), so the deliverable is code plus palettes, not binary or vector asset files.
- Stick to the existing conventions (lightweight ViewModels at call sites, repositories as boundaries, one `MaterialSymbolsRenderingScope` in `App.kt`, ktlint/detekt clean). Any new `expect`/`actual` needs justification in a code comment; the current design needs none.

## 1. Product decisions (authoritative)

1. The background displays a **park scene**: sky and hills behind, **trees to the sides**, a **path in the foreground**, sky above. Flat vector art style with **soft gradients**.
2. The scene adapts along two axes: **6 time bands** (dawn, morning, afternoon, dusk, evening, night) and **7 weather conditions** (clear, partly cloudy, overcast, rain, thunderstorm, snow, fog).
3. The time band follows the **local time of the selected weather location** (the location's UTC offset comes from the weather payload, section 3.3).
4. The background shows the **current weather everywhere**, never the forecast day selected on the Suggestions tab.
5. Rendering is a **single scene composition with per-condition colour palettes** and procedural weather overlays; one geometry, one palette per condition cell (section 4).
6. The scene is subtly **animated**: drifting clouds, falling rain and snow. No swaying trees, no parallax effects. Condition changes **crossfade over 2-3 s**.
7. The **Material3 theme follows the sky**: full scheme swap; the **dark scheme** applies at night, dusk, dawn, during thunderstorms, and during heavy rain; otherwise the light scheme (section 6.2).
8. The background is present in **all four tabs**, with graded visibility: lowest on the leftmost tab (Wardrobe, still visible), highest on the rightmost tab (Weather). Implementation is delegated (section 6.1 uses a per-tab scrim plus translucent cards).
9. When weather fails to load or is stale, the background shows a **neutral "partly cloudy" look for the current time band**.
10. Content is adjusted as needed for readability (translucent surfaces, scrim strength per tab).
11. This plan only specifies the feature; Jira tickets are created separately later.

## 2. Scene design

### 2.1 Composition

One landscape composition, drawn edge to edge behind the whole app, layered back to front:

1. **Sky**: full-canvas vertical gradient (top colour to horizon colour), the primary carrier of time of day. Optional celestial elements per band: sun disc with soft glow (dawn low in the east, morning climbing, afternoon high, dusk low in the west), a warm horizon glow at evening, moon and a scattering of stars at night.
2. **Clouds**: soft, rounded, flat-shaded cloud shapes drifting slowly; count and darkness carry the weather condition.
3. **Hills**: 2-3 overlapping rolling hill layers behind the tree line, using progressively darker/muted greens (or band-appropriate tones) for depth.
4. **Trees**: flat-canopy trees anchored to the left and right screen edges, framing the centre; canopy colour follows the palette, trunks simple dark silhouettes.
5. **Path**: a winding park path in the foreground, entering from the bottom edge and receding towards the centre of the hills; its fill lightens or darkens with the palette; optionally slightly desaturated and "wet"-looking during rain.
6. **Ground**: a foreground grass band under the path.
7. **Weather overlays** (procedural, section 5.3): rain streaks, snowflakes, fog veil, lightning flashes.

### 2.2 Responsive layout (phone to iPad to desktop)

The composition MUST NOT be a single fixed-aspect image scaled up. Instead:

- The sky gradient, hills and ground fill the whole canvas by construction (full-bleed gradients and horizontally tileable/repeatable hill curves).
- Side tree groups are **anchored to the left and right edges** (a fixed margin from the edge), so on wide viewports (iPad landscape, desktop window) the centre stays open and the park merely shows more hills, sky and path distance in the middle. Add a second, smaller background tree cluster per side that appears only on wide viewports so the edges do not look empty.
- The path is anchored to the bottom-centre and recedes towards the horizontal centre; its width scales with a fraction of the viewport width, clamped so it is neither a sliver on tablets nor enormous on phones.
- Everything is resolution independent (drawn in a normalised coordinate space mapped to the canvas size); test at 360 dp width (phone), 768 dp and 1024 dp+ (tablet/desktop) portrait and landscape.

### 2.3 Art direction

Flat vector shapes, no outlines, no texture noise; soft linear gradients for sky and soft radial glows for sun/moon; 2-3 shades per element maximum; long smooth curves for hills and canopy. Think "calm storybook park poster", not photorealism.

## 3. Condition model

New common package `shared/src/data/background/`.

```kotlin
enum class TimeBand { DAWN, MORNING, AFTERNOON, DUSK, EVENING, NIGHT }

enum class WeatherMood { CLEAR, PARTLY_CLOUDY, OVERCAST, RAIN, THUNDERSTORM, SNOW, FOG }

data class BackgroundCondition(
    val band: TimeBand,
    val mood: WeatherMood,
)
```

### 3.1 Time bands

Boundaries are **tunable constants** (hours, inclusive start, exclusive end), owner-approved defaults:


| Band      | Local hours   |
| --------- | ------------- |
| NIGHT     | 21:00 - 05:00 |
| DAWN      | 05:00 - 08:00 |
| MORNING   | 08:00 - 12:00 |
| AFTERNOON | 12:00 - 17:00 |
| DUSK      | 17:00 - 19:00 |
| EVENING   | 19:00 - 21:00 |


`TimeBand.from(localHour)` is a pure function; test it at every boundary.

### 3.2 Weather mood

Mapped from the OpenWeather icon code prefix of the current weather snapshot (same icons the app already uses): `01` CLEAR, `02` PARTLY\_CLOUDY, `03` and `04` OVERCAST, `09` and `10` RAIN, `11` THUNDERSTORM, `13` SNOW, `50` FOG. `WeatherMood.from(iconCode)` is pure and tested.

### 3.3 Local time of the selected location

The forecast API response carries the location's UTC offset (`city.timezone`, seconds). Extend the weather parsing to keep it and add `utcOffsetSeconds: Long` to `WeatherSnapshot` (additive, default 0). Local time = `Instant + utcOffsetSeconds`; the hour for the time band comes from that local time, not the device clock. When no offset is known (fallback place, error state), fall back to the device's local clock.

### 3.4 Deriving the condition

`BackgroundConditionResolver` (pure, testable):

```
condition(place snapshot + clock) =
    if (weather loaded)  TimeBand.from(localHour(offset)) x WeatherMood.from(icon)
    else                 TimeBand.from(localHour(offset)) x PARTLY_CLOUDY   // decision 9
```

While the app is open, a lightweight ticker flow (check every 60 s, cancelable) recomputes the condition so dusk/night transitions happen live and crossfade in.

## 4. Palettes

### 4.1 Structure

One palette type for the whole scene:

```kotlin
data class BackgroundPalette(
    val skyTop: Color, val skyHorizon: Color,
    val sunDisc: Color?, val sunGlow: Color?,      // null = no celestial disc
    val moonDisc: Color?, val starColor: Color?,
    val hillFar: Color, val hillMid: Color, val hillNear: Color,
    val treeCanopy: Color, val treeCanopyDark: Color, val treeTrunk: Color,
    val grass: Color, val pathFill: Color, val pathEdge: Color,
    val cloudLight: Color, val cloudDark: Color,
    val fogColor: Color?,                          // non-null only for FOG
)
```

Palettes are constants in `BackgroundPalettes.kt`. Recommended authoring pattern: define one **base palette per time band** (6) and one **mood modifier** per weather condition (desaturation/darkening of sky and hills, cloud density and darkness, fog veil), then compose them, with hand-tuned overrides where the composed result looks wrong. Alternatively hand-author all 42 cells explicitly. Either way, all 42 cells MUST resolve to a non-null palette (tested).

### 4.2 The 42 situations

Owner-approved art direction per cell (implementer refines exact colours):


| Band      | Clear                                                                          | Partly cloudy                            | Overcast                                 | Rain                                          | Thunderstorm                                   | Snow                                                    | Fog                                                |
| --------- | ------------------------------------------------------------------------------ | ---------------------------------------- | ---------------------------------------- | --------------------------------------------- | ---------------------------------------------- | ------------------------------------------------------- | -------------------------------------------------- |
| DAWN      | Peach horizon into soft violet sky, low warm sun in the east, dewy pale greens | Same warm sky, a few rose-tinted clouds  | Muted lilac-grey sky, pale hills         | Cool grey-pink sky, dim light, wet path sheen | Brooding violet-grey, distant lightning        | Pale pink-grey sky, white-ground contrast, soft shadows | Warm-tinted heavy veil, sun a soft disc behind fog |
| Morning   | Clear soft-blue sky, fresh greens, crisp path, climbing sun                    | Fresh blue sky with white puffy clouds   | Even pale blue-grey, flattened greens    | Grey-blue sky, steady rain, saturated greens  | Dark slate sky, flashes behind clouds          | Bright white-grey sky, snow on hills and path edges     | Bright haze, trees fading with distance            |
| Afternoon | Vivid blue sky, high bright sun, warm greens                                   | Classic blue sky, scattered white clouds | Flat grey-blue, muted mid-tones          | Dark grey-blue, glossy wet path, dark clouds  | Charcoal sky, frequent lightning, deep shadows | Cool white sky, snow highlights                         | Silvery haze, distant hills dissolving             |
| Dusk      | Orange-pink gradient, low sun in the west, long warm tones                     | Pink-orange sky, golden-lit clouds       | Dull mauve-grey, dimming scene           | Blue-grey with warm horizon hint, dark rain   | Near-black storm sky, strobe flashes           | Pink-tinted snow, soft violet sky                       | Mauve veil, trees as silhouettes                   |
| Evening   | Deep blue gradient, warm horizon glow below, first stars                       | Indigo sky, few dark-lit clouds          | Flat dark blue-grey, low contrast        | Dark navy-grey, faint rain                    | Black-blue, occasional flash                   | Indigo sky, snow faintly glowing                        | Dense dark veil, haloed lights                     |
| Night     | Deep indigo sky, moon and star field, near-black silhouettes                   | Moon between dark clouds, fewer stars    | Opaque low-contrast sky, faint moon glow | Near-black blue-grey, rain catching moonlight | Near-black, sheet lightning                    | Dark violet sky, snow bright against dark shapes        | Muted grey-blue, haloed moon, heavy veil           |


Additional per-mood scene notes:

- **Rain**: fewer/no celestial discs; clouds dominate; rain streaks drawn at a slight angle; path gets a subtle lighter "wet" tint.
- **Thunderstorm**: darkest palette of any band; clouds nearly black; occasional whole-sky brightening flash (subtle, max once every \~10 s, never full white).
- **Snow**: hills, grass and path edges shift towards whites/blues; snowflakes drift with slight sway.
- **Fog**: a translucent fog veil band across the mid-ground; hills and trees lose contrast with distance; cloud count low or the sky itself is the veil.

## 5. Rendering

### 5.1 Scene composable

`shared/src/ui/background/ParkScene.kt`:

- A single `Canvas`-based composable `ParkScene(condition, modifier)` filling its constraints (`Modifier.fillMaxSize()`), drawing the layers of section 2.1 in order with the palette for the condition. All drawing is common code, no platform APIs, no asset files.
- Build paths and brushes lazily keyed on the palette and canvas size; never allocate per frame.
- The scene MUST look correct at any aspect ratio (section 2.2) and is drawn in a normalised coordinate space mapped to the actual size.

### 5.2 Crossfade

Condition changes (weather refresh, time band rollover) crossfade over **2.5 s**: keep the previous scene rendered and animate the new one over it (e.g., `Crossfade(condition, animationSpec = tween(2500))` around `ParkScene`, or an alpha-animated overlay). The Material scheme swap (section 6.2) takes effect at the crossfade midpoint.

### 5.3 Ambient animation

All motion is time-based from a single `withFrameNanos` clock, paused automatically when the app is not visible:

- **Clouds drift** slowly horizontally (full traverse in minutes, wrapping around), 2-5 clouds depending on mood, bobbing imperceptibly.
- **Rain**: thin streaks falling at a constant slight angle, \~40 particles, recycled; intensity constant per mood (no storm bursts beyond the flash).
- **Snow**: \~60 slow flakes with sinusoidal sway, varied sizes and speeds.
- **Fog**: the veil band slowly thickens and thins (period \~30 s) and creeps sideways.
- **Lightning** (thunderstorm only): a whole-sky brightness pulse lasting \~200 ms, at most once every 10 s, randomized.
- Particle counts are tunable constants with modest defaults; desktop and WASM must render at 60 fps in release builds.

### 5.4 Data flow into the UI

- A shared `AppBackgroundController` (lightweight ViewModel created in `App.kt`) exposes `condition: StateFlow<BackgroundCondition?>` derived from the selected place's **current weather**, the shared `SelectedLocationStore` (journal plan section 8), and the 60 s ticker.
- Weather loading is hoisted: a small shared weather state at app-shell level (loaded once per place selection, reloaded on place change) feeds both the background and the Weather tab, which currently loads its own copy. Keep the Weather tab's behaviour identical from the user's perspective; refactor its controller to consume the shared flow instead of a duplicate request.
- `App.kt` composes: scene behind everything, per-tab scrim on top of the scene, then the existing `Scaffold` (now transparent container) with the pager. The current hard-coded `Color(0xFFF6F8FC)` scaffold background is removed in favor of the scene + scrim + scheme surface colors.

## 6. Visibility and theme

### 6.1 Per-tab visibility (scrim)

The scene is always fully drawn; visibility is controlled by a scrim layer between the scene and the content, whose alpha is a **tunable constant per tab**, interpolated smoothly while swiping the pager (`animateFloatAsState` on the settled/current page).
The scrim colour follows the active scheme (light scheme: near-`surface` tone; dark scheme: near-black with slight transparency). Content cards become translucent (`surface` colours with alpha, tuned for text contrast) so the scene shows through more on the right-hand tabs; on the leftmost tabs the scrim keeps grid text fully readable.

### 6.2 Theme following the sky

- Define two Material3 colour schemes (light and dark) once, matching the existing palette style; dark is derived for dark-sky conditions.
- **Dark scheme applies when**: `band in {NIGHT, DUSK, DAWN}` or `mood == THUNDERSTORM` or (`mood == RAIN` and icon indicates heavy rain, i.e. prefix `10` outside daylight bands, or `09/10` at night) as a tunable rule; simplest binding rule: `NIGHT, DUSK, DAWN, THUNDERSTORM` always dark; `RAIN` dark only in those same three bands. Document the chosen rule as constants.
- All screens must remain readable under both schemes: check text contrast on every tab, in dialogs (Wardrobe add/edit, Journal dialogs, survey), the weather cards, and the calendar; adjust surface alpha and scrim strength where needed (this is the "adjust content" mandate of decision 10).
- Android: with edge-to-edge, keep status-bar icon contrast aligned with the scheme.

## 7. Placement and integration

- All four tab screens render over it; none sets its own opaque full-screen background any more (the tab screens currently rely on the scaffold colour).
- The scene and scrim MUST NOT intercept touches (drawing-only modifiers) and MUST NOT affect the pager's gestures.
- Under `LocalInspectionMode`, render a cheap static fallback (single flat colour) so previews stay fast.

## 8. Implementation order

1. Condition model + resolver + offset plumbing + tests (section 3).
2. Palette system authored for all 42 cells (section 4) with palette tests.
3. `ParkScene` static rendering + crossfade + responsive layout (sections 2, 5.1, 5.2); visually verify on Android phone, desktop and web window sizes.
4. Ambient animation (section 5.3).
5. Shell integration: shared weather state, scrim per tab, transparent scaffold, scheme swap, content readability pass (sections 5.4, 6, 7).
6. UI tests (section 8).

Each step ends with `./kotlin build` and `./kotlin test` green, ktlint/detekt included.

## 9. Out of scope (explicitly)

- Seasons or day-of-year effects, location-specific scenes, parallax/gyroscope motion, weather sounds.
- Live wallpaper or app-icon theming.
- A settings surface or manual override of the background (fixed behaviour).
- Unit tests snapshotting exact colours of all 42 cells (hand-tuned colours are reviewed visually).
