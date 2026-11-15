# Petrichor: Presentation mode

Implementation plan for a presentation build of Petrichor for the big screen, targetable on JVM (desktop window) and WASM (browser). Written to be executed by an agentic AI developer. Read together with `APP_OVERVIEW.md`, `AGENTS.md`, the "Outfit Journal & Smart Recommendations" plan (*the journal plan*; the presentation shows that plan's tab structure and screens), and the background plan (*the background plan*; the adaptive park scene is visible throughout).

The presentation reuses the real app: its slides embed the actual tab screens and the actual background, live and fully interactive.

Last decision refresh: 2026-11-13.

---

## 0. How to use this document

- "MUST" / "always" / "never" statements are binding. "Should" statements are strong defaults.
- All decisions in section 1 were confirmed by the owner in an interview (2026-11-13). Do not re-litigate them.
- Stick to the existing conventions (lightweight ViewModels at call sites, repositories as boundaries, one `MaterialSymbolsRenderingScope` in `App.kt`, ktlint/detekt clean). Any new `expect`/`actual` needs justification in a code comment; this plan adds none beyond what is explicitly called out.

## 1. Product decisions (authoritative)

1. The presentation and the app share one codebase; the mode is chosen at **runtime, not compile time**: a JVM **system property / command-line argument** on desktop, a **URL parameter** on WASM. No compiler flag, no comment-in/out switching.
2. Targets: **both JVM and WASM**, whichever is practical on presentation day.
3. Navigation: a **forward button bottom-right**, a **backward button bottom-left**, plus **keyboard** (arrow keys, spacebar). Slide transitions: **horizontal slide, next slide enters from the right**, backward mirrors it.
4. Eleven slides, in order:
   1. Title: **"Petrichor" in large letters**, nothing else.
   2. Short overview of what the app does.
   3. The **Wardrobe** tab.
   4. The **Journal** tab (add an outfit, rate it).
   5. The **Suggestions** tab.
   6. The **Weather** tab.
   7-11. **Five placeholder slides** with generic example text and example lists.
5. The tab slides embed the **real tab screens, fully interactive**: on stage, outfits and clothing pieces are added live, surveys are filled in live. The app starts **empty** in presentation mode; everything is added live.
6. Slides keep the **app's real tab names**: Wardrobe, Journal, Suggestions, Weather.
7. Weather comes from the **live OpenWeather API** (internet required on stage, accepted).
8. The **adaptive park background** (background plan) is visible throughout, with a **light scrim behind slide content only** (no per-tab scrim logic in presentation mode).
9. The window **starts windowed**; the presenter maximizes manually. No fixed aspect ratio, no forced fullscreen.
10. No Jira tickets for this document; it is handed to an implementer directly.

## 2. Mode switching

Common:

```kotlin
// shared/src/AppMode.kt
enum class AppMode { APP, PRESENTATION }
```

- `App(mode: AppMode = AppMode.APP)` gains the parameter (default keeps every existing call site and the Android/iOS apps unchanged; those platforms are always `APP`).
- Platform entry points decide the mode **before** any storage or composition is initialized:
  - **JVM** (`desktopApp/main.kt`): `PRESENTATION` when the program args contain `--presentation` or the system property `petrichor.mode` equals `presentation`. Document the invocation in the README (e.g. `./kotlin run -m desktopApp -- --presentation` as the toolchain allows).
  - **WASM** (`webApp/main.kt`): `PRESENTATION` when the page URL contains `?mode=presentation` (read via the browser location available in that source set).
  - **Android / iOS**: always `APP`; no code reads a switch there.
- Entry points then compose `PetrichorPresentation()` instead of `App()` for `PRESENTATION` (section 4), after running the same storage initialization the normal app runs (below).

**Isolated, empty demo data.** Presentation mode MUST start from an empty app at every launch and MUST NOT touch the normal app's data. On JVM, presentation mode uses a separate data root (e.g. `~/.petrichor-presentation/` for the wardrobe DB, images, and the locations DataStore) instead of `~/.petrichor/`; on WASM it uses presentation-suffixed storage names (DataStore preference key prefix and the SQLite OPFS database name) so the browser profile's real app data is untouched. This is implemented where those paths/names are chosen in the JVM and wasmJs platform code; the mode is already known there at initialization time, so it is a parameter read at the existing initialization site, not a new `expect`/`actual`. Other platforms are unaffected.

## 3. Presentation shell

New common package `shared/src/ui/presentation/`.

### 3.1 Deck

```kotlin
@Composable
fun PetrichorPresentation(background: park scene plumbing from the background plan)
```

- A `PresentationDeck` holding `slideIndex` (`rememberSaveable` is unnecessary; the deck is the root), rendering the current slide with `AnimatedContent`:
  - forward: enter `slideInHorizontally { it } + fadeIn` (from the right), exit `slideOutHorizontally { -it } + fadeOut`; duration **350 ms** (tunable constant).
  - backward: mirrored (enter from the left).
- The deck itself MUST NOT be swipeable: slides are changed only by the nav buttons and the keyboard, so the deck never fights the embedded interactive tab screens' own scroll and pager gestures.

### 3.2 Navigation

- **Forward button**: bottom-right, Material3 filled button with an arrow icon (`MaterialSymbols` arrow/chevron), calls `next()`.
- **Backward button**: bottom-left, same style, calls `previous()`.
- Buttons are disabled at the ends (no looping).
- Buttons overlay the slides (bottom padding, above safe areas), drawn above the scrim, always visible on every slide.
- **Keyboard**: ArrowRight and Space = forward; ArrowLeft and Backspace = backward, via key event handling on the deck root; ignored while a text field has focus (so typing in the add-outfit/wardrobe dialogs never changes slides).
- A tiny slide indicator (e.g. "3 / 11") accompanies the buttons; subtle, tunable/omittable.

## 4. Slides

| # | Slide | Content |
|---|---|---|
| 1 | `TitleSlide` | "Petrichor" centered, display typography, nothing else |
| 2 | `OverviewSlide` | Short overview of the app (draft copy below, owner can edit) |
| 3 | `WardrobeTabSlide` | Embedded live `WardrobeScreen`, caption "Wardrobe" |
| 4 | `JournalTabSlide` | Embedded live `JournalScreen`, caption "Journal" |
| 5 | `SuggestionsTabSlide` | Embedded live `RecommendationScreen`, caption "Suggestions" |
| 6 | `WeatherTabSlide` | Embedded live `WeatherScreen`, caption "Weather" |
| 7-11 | `PlaceholderSlide` x5 | Generic example text + example list |

### 4.1 TitleSlide

"Petrichor" in large letters, centered on the park background, no scrim beyond the standard light one. Large means display-scale typography that also reads from the back of a room: scale with the window (a fraction of the shorter window dimension drives the font size).

### 4.2 OverviewSlide

Draft copy (owner-editable):

- Title: "What is Petrichor?"
- Lines: "Petrichor helps you decide what to wear."; bullets: "Your wardrobe, always with you" / "Log what you wore and how it felt" / "Personal comfort model, trained on you" / "Weather-aware outfit suggestions, every day".

### 4.3 Tab demo slides (3-6)

- Each embeds the **real screen composable** from the journal plan (`WardrobeScreen`, `JournalScreen`, `RecommendationScreen`, `WeatherScreen`), which create their own ViewModels at the call site exactly as in the app. The screens run against the real repositories, the live weather API, and the presentation-scoped empty database.
- A small caption chip with the tab name (Wardrobe / Journal / Suggestions / Weather) sits above the content; remove the caption if the owner asks.
- The embedded screen is centered in a **content column capped at ~720 dp width** (tunable) and full height, so a 1080p+ window shows the tab at a sensible size instead of a stretched grid. The background park scene fills the whole window around it.
- Everything in the screens works: dialogs, the piece multi-select, surveys, the location picker, the calendar. Interactions with these screens MUST NOT trigger slide changes (keyboard rule in 3.2; buttons are outside the content column).
- The survey/journal deep-link bus stays unused in presentation mode.

### 4.4 PlaceholderSlide

One composable, instantiated five times with an index parameter:

- Title: "Placeholder title <n>".
- Two or three sentences of generic example text ("This is example text. Replace it with your content.").
- An example list of four to five generic bullet items.
- Numbered "Slide <k> of 5" footer in small text. Clearly placeholder content, no feature meaning.

## 5. Background and scrim

- The deck composes the `ParkScene` and its condition plumbing from the background plan (shared `AppBackgroundController`-style weather flow for the selected/current place, live OpenWeather, live time bands). The park scene is visible on **every** slide.
- Presentation mode uses a **single light scrim**: one tunable alpha constant (default ~0.35, chosen for readability of slide text) applied to a layer **behind slide content**, instead of the app's per-tab scrim table. The scrim follows the active Material3 scheme (light/dark per the background plan's theme-follows-sky rule).
- Ambient animations (clouds, rain/snow, lightning) run as specified in the background plan; on the big screen they are part of the show.

## 6. Entry point wiring

- JVM `main.kt`: parse mode, initialize presentation-scoped storage root if in presentation mode, create the Compose `Window` (windowed, default size; no forced maximization), set content to `PetrichorPresentation()` or `App(mode)`.
- WASM `main.kt`: parse the URL parameter, initialize presentation-suffixed storage names, `ComposeViewport` content likewise.
- Android/iOS: unchanged; `App()` default mode.
- `README` gains a short section: how to launch the presentation on both targets (command line and URL), and that internet is required for live weather.

## 7. Testing

**Common (pure)**
- Mode parsing: JVM arg/property parser and WASM query parser as pure functions, tested for present/absent switch values.
- `PresentationDeck` state: forward/backward within bounds, disabled states at both ends, keyboard mapping table (key-to-action).

**UI (JVM, `runDesktopComposeUiTest`)**
- Title slide shows "Petrichor" text.
- Nav buttons advance/retreat the slide; content of slides 1, 2, 7 is assertable via semantics.
- Placeholder slide renders its example list items.
- Tab slides compose with fake repositories (a small fake-backed harness, consistent with existing UI tests); no assertions on live API behavior (network is out of test scope).
- Keyboard: slide index changes on synthetic key events where the test framework supports them; otherwise assert via the key-mapping table test.

## 8. Implementation order

1. Mode plumbing: `AppMode`, JVM/WASM parsing, isolated presentation storage, entry-point wiring (section 2, 6).
2. Deck shell: `PresentationDeck`, nav buttons, keyboard, transition animation (section 3).
3. Title + overview + placeholder slides (sections 4.1, 4.2, 4.4).
4. Background + scrim integration per the background plan (section 5).
5. Tab demo slides embedding the real screens (section 4.3).
6. Tests (section 7) and README notes.

Each step ends with `./kotlin build` and `./kotlin test` green, ktlint/detekt included. Dependencies: the journal plan's tab structure/screens and the background plan's park scene must have landed (or land together).

## 9. Out of scope (explicitly)

- Auto-play, timers, speaker notes, slide thumbnails, PDF export.
- A slide editor or content management; placeholders are code.
- Presentation mode on Android/iOS.
- Offline/canned weather fallback beyond what the background plan already specifies for load failures.
- Touch gestures for slide navigation.