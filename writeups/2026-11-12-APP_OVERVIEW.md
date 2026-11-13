# Petrichor — App Overview (for AI agents)

This document describes the **entire current state** of the Petrichor codebase: features, architecture, data model, platform differences, testing, and conventions. It is written so an AI agent can work productively in this repository without re-reading every file. Read it together with [AGENTS.md](../AGENTS.md), which holds the binding project rules.

Last refreshed: 2026-02, on branch `dev`, HEAD `991b28d`.

---

## 1. What the app is

Petrichor is a **clothing wardrobe app with weather-based outfit recommendations**, built as a Kotlin Multiplatform (KMP) + Compose Multiplatform app. Target platforms: **Android, Desktop (JVM), iOS, Web (WASM/JS)**.

Current feature set:

| Feature | Status |
|---|---|
| Wardrobe: browse clothing items in a grid, add/edit/delete with photos | Implemented |
| Wardrobe: filter by clothing type (dropdown) | Implemented |
| Wardrobe: detail screen (full-screen overlay with open/close animations) | Implemented |
| Weather tab: current conditions + 5-day forecast via OpenWeather | Implemented |
| Location: search (OpenWeather geocoding), save, remove, reorder favorite locations | Implemented |
| Location picker on the Weather tab (pick saved/searched/current location) | Implemented |
| Suggestions tab: calendar (week/month) + per-day outfit suggestion from wardrobe + forecast | Implemented (recommendation logic is still a stub) |
| Home and Settings tabs | Placeholders ("This tab is coming soon.") |

App name / id: `nl.petrichor.app` (Android applicationId & Kotlin namespace).

---

## 2. Build system, modules, commands

The project uses the **Kotlin Toolchain** (JetBrains' project system; there is no Gradle). Module structure is declared in YAML files.

- `../kotlin` / `kotlin.bat` in the repo root: self-bootstrapping wrapper that downloads the pinned toolchain.
- `../project.yaml` (root): lists modules and the shared product definition.
- `<module>/module.yaml`: per-module config.

```shell
./kotlin build                                  # build everything
./kotlin run -m androidApp                      # run Android app
./kotlin run -m desktopApp                      # run desktop app
./kotlin run -m iosApp                          # run iOS app
./kotlin test                                   # all tests
./kotlin test -m shared                         # tests for one module
./kotlin build -m webApp -p wasmJs -v release   # deployable WASM build
```

WASM output goes to `build/tasks/_webApp_buildWasmJsAppWasmJsRelease/`. It must be served over HTTP with the headers `Cross-Origin-Opener-Policy` and `Cross-Origin-Embedder-Policy` (required by the SQLite OPFS worker). `../webApp/serve.py` does this for local smoke tests.

### Modules

| Module | Type | Contents |
|---|---|---|
| `shared` | KMP library | All app code (UI, data, business logic) + platform-specific actuals |
| `androidApp` | Android app (`minSdk 26`) | `MainActivity.kt` only; initializes DB, DataStore, image storage, sets content to `App()` |
| `desktopApp` | JVM app | `main.kt` opening a Compose `Window` running `App()` |
| `iosApp` | iOS app | `ViewController.kt` (`ComposeUIViewController { App() }`), `Info.plist` with camera/photo/location usage strings |
| `webApp` | WASM target | `main.kt` using `ComposeViewport`; `web-worker/` holds the sql.js OPFS SQLite worker files; `serve.py` dev server |

### Shared source sets

- `../shared/src` — common code (Compose UI, repositories, models).
- `shared/src@<platform>` for `android`, `ios`, `jvm`, `wasmJs` — `expect`/`actual` implementations.
- Tests: `../shared/test` (common) and `shared/test@<platform>`.

---

## 3. Tech stack & key dependencies

Declared in `../shared/module.yaml` (`dependencies:` ...) and `libs.versions.toml`.

| Concern | Library | Notes |
|---|---|---|
| UI | Compose Multiplatform (runtime, foundation, material3, ui, resources, previews, hot reload) | |
| Lifecycle / DI of screens | `androidx.lifecycle` viewmodel + runtime compose (multiplatform) | ViewModels created via `viewModel { ... }` or `remember { ... }` in composables |
| Networking | Ktor client 3.x | Engine per platform: CIO (Android/JVM), Darwin (iOS), Js (WASM); `expect fun weatherHttpClient(): HttpClient` |
| Persistence (wardrobe) | **Room 3 multiplatform** (`androidx.room3.*`, KSP processor, bundled SQLite driver; `sqlite-web` + OPFS worker on WASM) | DB version 1, no schema export |
| Persistence (saved locations) | Jetbrains DataStore preferences-core | file `saved_locations.preferences_pb` |
| Geolocation | Compass (`dev.jordond.compass:geolocation`, `-mobile` on Android/iOS, `-browser` on WASM) | JVM/WASM `currentCoordinates()` actuals return `null` → fallback coordinates |
| Images | `imagepickerkmp` (io.github.ismoy) 1.1.7 | gallery + optional square crop; camera availability gated per platform |
| Icons | `dev.catbit:material-symbols` (`MaterialSymbol`, `MaterialSymbolsRenderingScope`) | see rule in AGENTS.md: exactly one scope, in `App.kt` |
| Calendar | `com.kizitonwose.calendar:compose-multiplatform` 2.10.1 (`HorizontalCalendar`, `WeekCalendar`) | used by the Suggestions tab |
| Reordering | `sh.calvin.reorderable:reorderable` 3.1.0 | drag-to-reorder saved locations |
| Dates / JSON | kotlinx-datetime 0.8.0, kotlinx-serialization-json 1.12.0 | |
| Testing | kotlin.test, kotlinx-coroutines-test, `$compose.uiTest` + Linux Skiko runtime (JVM UI tests, `runDesktopComposeUiTest`) | |

---

## 4. Navigation & app shell

`../shared/src/App.kt` is the single entry composable:

- Wraps everything in the app's single `MaterialSymbolsRenderingScope { ... }` (project rule: never create another one).
- Material3 `Scaffold` with light container color `Color(0xFFF6F8FC)` and a `NavigationBar`.
- Content: `HorizontalPager` with **5 pages** = 5 bottom tabs (swiping + tapping both switch):

| Tab # | Label | Icon | Screen |
|---|---|---|---|
| 0 | Wardrobe | `CHECKROOM` | `WardrobeScreen` (no extra status-bar padding; the filter bar adds its own) |
| 1 | Home | `HOME` | placeholder `BoxMessage` |
| 2 | Settings | `SETTINGS` | placeholder `BoxMessage` |
| 3 | Suggestions | `FOR_YOU` | `RecommendationScreen` |
| 4 | Weather | `CLOUD` | `WeatherScreen` |

State: selection = `pagerState.currentPage`; no navigation library, no deep links. `LocalInspectionMode` short-circuits `WardrobeScreen` and `RecommendationScreen` to a preview message.

---

## 5. Domain model (all in `../shared/src/data`)

### Weather (`data/weather/`)

```kotlin
Coordinates(latitude: Double, longitude: Double)   // @Serializable
Place(name: String, coordinates: Coordinates)
WeatherSnapshot(place, temperature, feelsLike, description, icon, humidity, windSpeed, forecast: List<ForecastDay>)
ForecastDay(label: String /* "YYYY-MM-DD" date string */, min: Int, max: Int, icon: String)
```

### Wardrobe (`data/wardrobe/`)

```kotlin
enum Material { COTTON, WOOL, POLYESTER, LINEN, LEATHER, SILK, MIX, UNKNOWN }   // displayName strings
enum ClothingSupertype { UPPER_BODY, LOWER_BODY, FULL_BODY, FOOTWEAR, HEADWEAR, ACCESSORIES }

@Entity clothing_types: ClothingType(id auto, name, supertype)   // unique index (supertype, name)
@Entity clothing:       ClothingPiece(id auto, name?, typeId FK->clothing_types RESTRICT, material,
                          notes?, imagePath, thumbnailPath, createdAt)  // index on typeId
```

Room DB: `PetrichorDatabase` in `data.database` (`petrichor.db`, v2; `PetrichorConverters` converts enums by name; unknown Material → `UNKNOWN`, unknown supertype → throws). DAO: `ClothingDao` (`getAll` ordered by `createdAt DESC`, `getByType`, `getById`, insert/update/delete/deleteAll, `getTypes`, `insertTypes(IGNORE)` for seeding, `insertType`).

**Seeding:** `WardrobeRepository.getClothingTypes()` (a Flow) is `.onStart { insertTypes(BuiltInClothingTypes.all) }` — 12 built-in types: T-Shirts, Hoodies, Sweaters, Shirts, Jackets (UPPER_BODY); Jeans, Pants, Shorts (LOWER_BODY); Dresses (FULL_BODY); Sneakers (FOOTWEAR); Caps (HEADWEAR); Ties (ACCESSORIES).

**Timestamp rule:** `addClothing()` stamps `createdAt = Clock.System.now().toEpochMilliseconds()` only when the incoming entity has `createdAt == 0`; edits preserve it. All mutations are wrapped in `withContext(ioDispatcher)`. Mutation results surface as snackbar strings ("Clothing item added!/updated!/deleted!") via `WardrobeViewModel.snackbarMessage`.

### Location (`data/location/`)

```kotlin
@Serializable LocationResult(name, region = "", coordinates)  // .key = "lat,lon"; .toPlace()
```

- `LocationSearchRepository.search(query)`: OpenWeather `GET /geo/1.0/direct?q&limit=5&appid`, maps to `LocationResult`, dedupes by (name, region).
- `SavedLocationsRepository`: stores the list as JSON in one DataStore preference key `saved_locations`; `add` (dedupe by key), `remove`, `update(transform)`, `locations: Flow`.

### Recommendation (`data/recommendation/OutfitRecommendation.kt`) — internal

```kotlin
DayWeather(isRainy, minTemperature?, maxTemperature?)
LayerRecommendation(label, options: List<ClothingPiece>, generalSuggestion: String)
OutfitRecommendation(headwear, baseUpper, outerUpper, lowerBody, footwear)  // 5 layers
```

- `WeatherSnapshot.forecastDates(today)`: set of `LocalDate` parsed from forecast labels + today.
- `WeatherSnapshot?.dayWeatherFor(date, today)`: null snapshot or a future date without forecast → defaults (not rainy, no temps). Rain detection: today's description contains "rain", or OpenWeather icon code prefix `09/10/11`. Temps: for today, min/max of current temperature and today's forecast entry; else from the forecast entry.
- `recommendOutfit(...)`: **placeholder logic** (has a TODO: replace with ranking based on weather). Groups items by supertype; UPPER_BODY items are partitioned into outer layer (type name in `{"Jackets","Hoodies","Sweaters"}`) and base layer. Each layer carries the user's actual items as options plus a hard-coded textual `generalSuggestion` that changes by `isRainy` (e.g. "Rain jacket", "Waterproof shoes", "Long pants").

---

## 6. Features in detail

### 6.1 Wardrobe tab (`ui/wardrobe/`)

- **`WardrobeScreen`** (305 lines) owns all screen state: selected filter type id, add-dialog visibility, `rememberSaveable` selected clothing id + detail-dialog visibility + `animateDetail` flag, `closingClothing` for the exit animation, and a `SnackbarHostState`.
  - `WardrobeViewModel` (simple class, created with `remember`): exposes `allClothing`/`clothingTypes` flows, `addClothing`/`updateClothing`/`deleteClothing` with error snackbars. Delete also removes the stored image via `MediaStorage.deleteImage`; update removes the previous image only when `imagePath` changed.
  - Body: 2-column `LazyVerticalGrid` with the `WardrobeFilterBar` as a full-span first item, empty-state messages, `ClothingCard` per item. FAB ("Add clothing", `ADD` symbol) hidden while detail is open. `BackHandler` (platform expect) closes the detail screen.
  - Detail overlay: `AnimatedVisibility` with `MutableTransitionState(!animateDetail)` — slide+fade in on open, slide+fade out on close, kept rendered from `selectedClothing ?: closingClothing` so the exit animation completes before the selection is cleared (binding rule in AGENTS.md).
- **`WardrobeFilterBar`**: icon + `DropdownMenu`; "All Types" reports `null`, otherwise the type's id. Filtering itself happens in `WardrobeScreen` (rule).
- **`ClothingCard`**: square image card; uses `loadImagePainter(imagePath)` (expect) or a fallback `IMAGE` symbol; optional single-line name overlay at the bottom.
- **`ClothingDetailScreen`** (234 lines): full-screen overlay with hero image, name, detail rows (Type / Material / Notes) via `DetailRow`, an Edit FAB, overflow menu with Delete (calls `onDelete`), and a close button ("Close") calling `onDismiss`.
- **`AddClothingDialog`** (381 lines) shared by add and edit (`clothing = null` → add):
  - Fields: name, type dropdown (required for new items; save disabled without it), material dropdown, notes. Uses `remember` (dialog-local) state.
  - Image: `rememberImagePickerKMP` (`GalleryConfig(allowMultiple = false)`, `CropConfig(enabled = isImageCropAvailable, squareCrop = true)`), "Select Image" / replace flow with `useNewImage` state, preview via `MediaStorage.loadPhotoPainter`.
  - On save: copies the picked photo to permanent storage (`MediaStorage.copyToPermanentStorage`) or reuses the existing path, then builds/updates a `ClothingPiece` (`thumbnailPath` currently always equals `imagePath`), and calls `onSave` (suspend; shows `saveError` on failure, keeps dialog open on repo failure).

### 6.2 Weather tab (`ui/WeatherScreen.kt`)

- `WeatherController : ViewModel` with sealed `WeatherUiState` (Loading / Ready / Error), loads via `WeatherRepository.load(place)`.
- Screen keeps `selectedPlace: Place?` in Compose state; `LaunchedEffect`: when `null` it resolves `currentPlace()` (geolocation or fallback); otherwise reloads. Selecting a location sets the place; "Use current location" resets to `null`.
- Header (`LocationHeader`): location label + toggle button opening the **`LocationPicker`** inline.
- Content card: description, big temperature (°C), "Feels like", humidity/wind line; horizontal `LazyRow` of 5 forecast cards (label is the raw `YYYY-MM-DD` with everything before the first `-` stripped, i.e. `MM-DD`).

### 6.3 Location picking (`ui/LocationPicker.kt`, `ui/LocationPickerController.kt`, shared by Weather tab; also usable elsewhere)

- `LocationPickerController : ViewModel` holds `LocationPickerUiState(query, search: LocationSearchState, saved)`. Search is **debounced 350 ms** with `minQueryLength = 2`; states: Idle / Loading / Ready(results) / Error.
  In idle (blank query) mode it shows "Use current location" plus the saved list; in search mode it shows results with add/remove actions (checked state synced with saved keys).
- Saved locations are drag-reorderable (`ReorderableItem`, `longPressDraggableHandle`, reorderable library); local reorder via `controller.move`, committed to persistence with `controller.commitOrder()` on drag stop.
- Designed as a shared composable: on-selection callbacks `onLocationSelected(Place)` / `onCurrentLocationSelected()`; the Weather tab collapses search state on close.

### 6.4 Suggestions tab (`ui/recommendation/`)

- **`RecommendationScreen`**: `RecommendationController : ViewModel` → loads `currentPlace()` then `WeatherRepository.load`; exposes `locationName` and `weather` state flows. Collects `allClothing` + `clothingTypes` flows from `WardrobeRepository`. Selected date is `rememberSaveable` ISO string (defaults to today).
  - Layout: `RecommendationCalendar` on top, then `AnimatedContent` (fade+scale+slide, ~110–280 ms) keyed on the selected date rendering `LocationNote` + four body-row sections: Headwear, Upper body (base + outer layers), Lower body, Footwear. Outfit is recomputed with `remember(allClothing, clothingTypes, dayWeather) { recommendOutfit(...) }`.
- **`RecommendationCalendar`** (343 lines): kizitonwose calendar wrapped in a Card; toggles between **week view** (`WeekCalendar`) and **month view** (`HorizontalCalendar`) with `showFullMonth` in `rememberSaveable`; range spans ±500 months around `today`. Header row: prev/next page chevrons, "Month Year" title, animated "Today" chip (only when selected date off-screen/off-today), and a view-mode toggle icon. Day cells (`CalendarDay`): 36 dp circle, selected fill `colorScheme.primary`, today outline+primary text, 4-dot indicator when the day has forecast data, semantics (`selected`, click label "Select <Day dd Month>"), month-view days outside the current month are disabled with alpha 0. Exposes `calendar_pager` test tag and the internal `consumeHorizontalScroll` nested-scroll connection (hands horizontal scrolls to inner pagers, reused by `SwipeableClothingSlot`).
- **`SwipeableClothingSlot`** (140): 136 dp column; optional layer label; `HorizontalPager` cycling the layer's items (each page a `ClothingCard`, tap = next) or, when empty, a `RecommendationSlotCard` showing the textual suggestion; below it prev/next chevrons and animated pagination dots (max 5).
- **`LocationNote`**: "Based on <location>" + " • max° / min°" when temps are known.

---

## 7. Platform-specific implementations (`shared/src@<platform>`)

`expect` declarations in common code and their actuals:

| expect (common) | Android | JVM/Desktop | iOS | wasmJs |
|---|---|---|---|---|
| `data.weather.weatherHttpClient()` | Ktor `HttpClient()` (CIO engine via dependency) | `HttpClient()` (CIO) | `HttpClient()` (Darwin) | `HttpClient(Js)` |
| `data.currentCoordinates()` | Compass `Geolocator.mobile().current()` | returns `null` | Compass mobile | returns `null` |
| `data.location.createLocationDataStore()` | app `filesDir/saved_locations.preferences_pb` (must `initLocationDataStore(context)` from the Activity first) | `~/.petrichor/saved_locations.preferences_pb` | Documents dir | bare path (localStorage-backed) |
| `data.database.PetrichorDatabaseProvider` | `Room.databaseBuilder` + `BundledSQLiteDriver`, `Dispatchers.IO`; requires `init(context)` from `MainActivity` | DB in `~/.petrichor/petrichor.db` | Bundled driver, Documents dir | `WebWorkerSQLiteDriver` over the OPFS worker (`@androidx/sqlite-web-worker/worker.js`) |
| `data.media.MediaStorage` actuals (`saveMediaImage`, `deleteStoredImage`, `imageUriForLoading`, `loadImagePainter`, `loadPickedPhotoPainter`) | files in `filesDir/wardrobe-images/clothing_<millis>.<ext>`; paths used as-is | files in `~/.petrichor/wardrobe-images/`; loading via `file:` URI | files in Documents `wardrobe-images/clothing_<uuid>.<ext>`; loading via absolute `file://` URL (**stored path is relative to Documents**) | images stored as **base64 data URLs in the DB row**; delete is a no-op; uses `@JsFun` fetch → bytes → Skia `Image` |
| `platform.BackHandler` | real `androidx.activity.compose.BackHandler` | no-op | no-op | no-op |
| `platform.isCameraAvailable()` | `true` | `false` | `UIImagePickerController.isSourceTypeAvailable(.camera)` | `false` |
| `platform.isImageCropAvailable` | `false` | `true` | (check `ImageCropAvailability.ios.kt`) | `false` |

Platform entry points: Android `MainActivity.kt` (edge-to-edge, calls the three init functions before `App()`); desktop & web `main.kt`; iOS `ViewController.kt` (glued by `iosApp.swift`).

Platform chrome/permissions: Android manifest requests `CAMERA`, `uses-feature camera required=false`, `IMAGE_CAPTURE` query, `Theme.Material.Light.NoActionBar`; iOS `Info.plist` has `NSLocationWhenInUseUsageDescription`, `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`.

Note: `../shared/src/AppConfig.kt` contains `OPENWEATHER_API_KEY` (a real key is committed in the repo) and `OPENWEATHER_API_BASE = https://api.openweathermap.org`. The README still mentions a `WeatherConfig` key slot — the live location is `AppConfig`.

---

## 8. Tests

Common (`../shared/test/data/wardrobe`):
- `ClothingTypeTest` — built-in types: unique (supertype, name); cover all supertypes; id-0 defaults.
- `PetrichorConvertersTest` (in `../shared/test/data/database`) — Material round-trips, unknown-string fallback, supertype conversions.
- `WardrobeRepositoryTest` — timestamp stamping/preservation, built-in type seeding on first type-flow collection, DAO delegation. Uses a hand-written `FakeClothingDao` ( MutableStateFlow-backed) so no Room driver is needed (rule in AGENTS.md).

Per-platform (`shared/test@<platform>/data/media/MediaStorageTest.kt`) — image path → loadable URI conversions per platform (Android raw paths, iOS `file://` URLs with %-encoding, JVM `file:` URIs).

JVM Compose UI tests (`../shared/test@jvm/ui`, `runDesktopComposeUiTest`, content wrapped in `MaterialSymbolsRenderingScope` — test-only scope, the production scope stays in `App.kt`):
- `ui/wardrobe/ClothingCardTest.kt` — image/name semantics.
- `ui/wardrobe/WardrobeFilterBarTest.kt` — menu shows "All Types" + types, reports null / type id.
- `ui/recommendation/RecommendationCalendarTest.kt` — week default + selected day, day click reports date, swipe advances a week (calendar anims are tested on JVM with a fixed `today`).

Run: `./kotlin test`.

---

## 9. Conventions & project rules (from AGENTS.md — binding)

- Keep the Compose architecture simple; keep changes focused, no unrelated refactors.
- Exactly **one** `MaterialSymbolsRenderingScope`, defined in `App.kt`; never create others (tests bind their own since they don't run through `App.kt`).
- Wardrobe state ownership: `WardrobeScreen` owns filter selection, dialog visibility, selected clothing id, detail transition state, and snackbars; the filter bar only reports; `AddClothingDialog` is shared add/edit.
- `WardrobeRepository` is the business boundary around `ClothingDao` (timestamps, seeding, IO dispatcher).
- Tests: wardrobe data tests in `../shared/test/data/wardrobe` with fakes; UI tests in `shared/test@jvm/ui/wardrobe`; assert user-visible semantics, not animation timing.

Other unwritten conventions observed in the code: no navigation framework (single pager shell); ViewModels are lightweight and constructed at call sites with default-argument repositories/clients (no DI framework); UI state as `StateFlow` + `collectAsStateWithLifecycle`; `weatherHttpClient()` must be overridable per platform (comment in `WeatherClient.kt`).

---

## 10. Known TODOs, gaps, limitations

- `recommendOutfit` is a placeholder (TODO at `OutfitRecommendation.kt:52`): no ranking by temperature/material; temperatures are only used for display.
- No weather/location caching between loads; suggestions reload weather only on first composition of the screen's ViewModel (per-tab pager page).
- `thumbnailPath` is always set equal to `imagePath`; no downscaled thumbnails are generated.
- JVM and WASM have no real geolocation (`currentCoordinates() == null`) → both silently use the fallback place **"Current location"** at Amsterdam (52.3676, 4.9041) (`data/Location.kt`).
- Weather tab: the forecast `label` is used both as date key and display (substring after `-`), so 5 forecast days are sampled at 3-hour steps `index % 8 == 0` (≈ 00:00 UTC entries of each day) — labels are bookkeeping, not localized days.
- Home and Settings tabs are placeholders; tab order (Wardrobe, Home, Settings, Suggestions, Weather) was chosen arbitrarily.
- Room schema export is disabled (`exportSchema = false`); DB version 1 with no migration story yet.
- Wardrobe detail screen overlays the whole tab pager page; swipe-to-change-tab remains possible beneath it (back handling only on Android).
