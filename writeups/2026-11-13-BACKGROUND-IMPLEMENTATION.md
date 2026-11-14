# Background implementation

Implemented from `2026-11-13-BACKGROUND.md` in these stages:

1. Add the pure time/weather condition model, selected-location offset parsing, and boundary tests.
2. Author six base palettes and mood treatments covering all 42 combinations.
3. Draw a responsive park with cached paths and brushes, edge-anchored trees, and a centred winding path.
4. Add a shared lifecycle-aware frame clock for clouds, rain, snow, fog, and restrained lightning, with a 2.5-second condition crossfade.
5. Hoist weather loading into the app shell, share it with Weather and Suggestions, apply tab scrims and translucent cards, and lerp the full Material scheme across the 2.5 s crossfade (Android system bar icons still switch at the fade midpoint).
6. Verify model/controller behaviour, rendering across viewport sizes, pointer interaction, and supported platform builds.

## Tunable decisions

- Weather becomes stale after one hour. Its mood falls back to partly cloudy while retaining a known location offset. Without a known offset, the device clock supplies local time.
- Dark mode uses the writeup's binding simple rule: dawn, dusk, night, or thunderstorms. Evening and daytime rain use light mode.
- Tab scrims are 0.60 / 0.50 / 0.35 / 0.10 for Wardrobe / Journal / Suggestions / Weather.
- Time boundaries live in `BackgroundCondition.kt`; palettes and the theme rule live in `ui/background`. Rain and snow use 40 and 60 particles. Lightning uses seeded irregular timings with at least ten seconds between pulses.
- The weather request and minute ticker stop below the STARTED lifecycle state. Returning to the foreground preserves a completed load and restarts an interrupted load. Location changes cancel the previous request.

## Review and validation

The JVM UI tests generate `shared/build/background-review/all-conditions.png` plus phone, tablet, and desktop night/snow renders. These are review artefacts, not exact-colour snapshot assertions. The scene has no pointer handlers; the tests verify that overlaid content receives clicks.

Physical-device visual checks, iOS runtime verification, and release frame-rate profiling require follow-up on the respective devices. No new platform-specific expect/actual declarations or rendering scopes were introduced.

Validated commands:

- `./kotlin test -m shared -p jvm -p android`: 122 JVM tests and 76 Android unit tests passed, including the new condition, controller, palette, rendering, and click-through checks.
- `./kotlin build -m desktopApp -m androidApp`: passed.
- `./kotlin build -m webApp -p wasmJs -v release`: passed.
- ktlint and detekt ran through the normal wrapper gates and passed.

The combined all-module web build exposed a toolchain race extracting Skiko into `build/temp/skiko-js-wasm-runtime`. Clearing that generated directory and building the web app separately succeeded. The toolchain also prints a Ktor WASM KLIB warning; this change does not alter those dependencies. The WASM test invocation exited successfully but did not produce an execution report, so it is not counted as a confirmed passing test suite here.

A headless Chromium smoke test also started the release web app, opened all four tabs at 360×800 and 1024×768, and recorded zero JavaScript page errors. Screenshots are in `shared/build/background-review/web-<tab>-<width>.png`; the phone Weather and desktop Wardrobe captures were inspected for contrast and graded scene visibility. These checks do not establish a device frame-rate guarantee.
