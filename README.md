This is a Kotlin Multiplatform project targeting Android, Desktop (JVM), and iOS, built with
the [Kotlin Toolchain](https://kotlin-toolchain.org/dev/).

- [/androidApp](./androidApp) contains the Android application.
- [/desktopApp](./desktopApp) contains the desktop (JVM) application.
- [/iosApp](./iosApp) contains the iOS application.
- [/shared](./shared) holds the code shared across your applications — Compose UI, business logic, and platform-specific
  implementations. [src](./shared/src) is for common code; the sibling `src@<platform>` folders (for example
  `src@android`) hold code compiled only for the platform named in the folder.

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts in the project root are self-bootstrapping wrappers for
the Kotlin Toolchain: they download the pinned toolchain version on first use, so no separate installation is required.
Build the whole project with `./kotlin build`.

### Running

Use the run configurations provided by the run widget in your IDE's toolbar. You can also run each module from the
command line:

- Android app: `./kotlin run -m androidApp`
- Desktop app: `./kotlin run -m desktopApp`
- iOS app: `./kotlin run -m iosApp`

### Building a deployable WASM version

The web application is compiled to Kotlin/WASM by the `webApp` module. Build its release
distribution with:

```shell
./kotlin build -m webApp -p wasmJs -v release
```

The deployable static files are written to
`build/tasks/_webApp_buildWasmJsAppWasmJsRelease/`. Upload the contents of that directory
to any static web host, keeping the generated `.wasm`, `.mjs`, and other asset files
alongside `index.html`. For a local smoke test, serve the directory over HTTP instead of
opening `index.html` directly:

```shell
python3 webApp/serve.py build/tasks/_webApp_buildWasmJsAppWasmJsRelease
```

The custom server adds the `Cross-Origin-Opener-Policy` and
`Cross-Origin-Embedder-Policy` headers required by SQLite's OPFS worker. Production hosting
must send the same headers for the application and its worker assets.

### Testing

Run the project's tests with `./kotlin test`, or `./kotlin test -m <module>` for a single module.

## Weather API setup

Petrichor uses OpenWeather for current conditions and the five-day forecast. Add your key to
`WeatherConfig.openWeatherApiKey` in `shared/src/data/weather/WeatherRepository.kt`.

---

Learn more
about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/dev/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/).
