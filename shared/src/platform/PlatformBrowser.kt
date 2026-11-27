package nl.petrichor.app.platform

/** Opens a URL in the external browser (system browser, new tab on web). */
expect object PlatformBrowser {
    fun open(url: String)
}
