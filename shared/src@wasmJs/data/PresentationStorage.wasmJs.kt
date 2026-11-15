package nl.petrichor.app.data

import nl.petrichor.app.AppMode

/**
 * Presentation mode on the web is announced by the page URL (`?mode=presentation`). The platform
 * actuals that pick storage names read it here so the browser profile's real app data (the OPFS
 * SQLite database and the locations DataStore) is never touched during a demonstration.
 */
internal fun isPresentationMode(): Boolean = AppMode.fromUrlQuery(pageQuery()) == AppMode.PRESENTATION

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.location.search")
private external fun pageQuery(): String
