package nl.petrichor.app.data.affiliate

import io.ktor.client.HttpClient

/** Realistic browser User-Agent so store pages are served the full HTML variant. */
internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

/**
 * HTTP client for the affiliate adapter, mirroring the `weatherHttpClient()` pattern, with a
 * realistic browser User-Agent so store pages are served the full HTML variant. On platforms
 * where the store is unreachable (notably WASM/CORS) requests fail and the feed degrades silently.
 */
expect fun affiliateHttpClient(): HttpClient
