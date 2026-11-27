package nl.petrichor.app.data.affiliate

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.UserAgent

/** Realistic browser User-Agent so store pages are served the full HTML variant. */
internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/158.0.0.0 Safari/537.36"

/**
 * The engine is the only platform-specific part of [affiliateHttpClient]; all other
 * configuration lives in common code.
 */
internal expect fun affiliateHttpClientEngineFactory(): HttpClientEngineFactory<*>

/**
 * HTTP client for the affiliate adapter, configured with a realistic browser User-Agent so
 * store pages are served the full HTML variant. On platforms where the store is unreachable
 * (notably WASM/CORS) requests fail and the feed degrades silently.
 */
fun affiliateHttpClient(): HttpClient =
    HttpClient(affiliateHttpClientEngineFactory()) {
        install(UserAgent) { agent = BROWSER_USER_AGENT }
    }
