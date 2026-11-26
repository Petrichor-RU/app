package nl.petrichor.app.data.affiliate

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

internal val scrapeJson = Json { ignoreUnknownKeys = true }

/**
 * GETs [url] as HTML with browser-like headers. Throws on request/HTTP errors — callers
 * degrade silently.
 */
internal suspend fun HttpClient.getHtml(
    url: String,
    acceptLanguage: String = "en-US,en;q=0.9",
): String =
    get(url) {
        header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        header(HttpHeaders.AcceptLanguage, acceptLanguage)
    }.bodyAsText()

/**
 * Extracts the balanced JSON object that starts at the first `{` at or after [marker] in [text],
 * honouring string escapes. Returns null when the marker is absent or the object never closes.
 */
internal fun extractJsonObject(
    text: String,
    marker: String,
    fromIndex: Int = 0,
): String? {
    val markerIndex = text.indexOf(marker, fromIndex)
    if (markerIndex < 0) return null
    return extractBalanced(text, markerIndex + marker.length, open = '{', close = '}')
}

private fun extractBalanced(
    text: String,
    fromIndex: Int,
    open: Char,
    close: Char,
): String? {
    var i = fromIndex
    var result: String? = null
    while (i < text.length && result == null) {
        val c = text[i]
        if (c == '"') {
            // Skip string literals so a brace inside a string cannot mis-start the object.
            val stringEnd = skipString(text, i)
            i = if (stringEnd != null) stringEnd else text.length
        } else if (c == open) {
            val end = scanBalancedEnd(text, i, open, close)
            if (end >= 0) {
                result = text.substring(i, end + 1)
            } else {
                i = text.length
            }
        } else {
            i++
        }
    }
    return result
}

/** Returns the index of the [close] matching the [open] at [start], or -1 when unbalanced. */
private fun scanBalancedEnd(
    text: String,
    start: Int,
    open: Char,
    close: Char,
): Int {
    var depth = 0
    var i = start
    var end = -1
    while (i < text.length && end < 0) {
        val c = text[i]
        when (c) {
            '"' -> {
                val stringEnd = skipString(text, i) ?: return -1
                i = stringEnd
                continue
            }

            open -> {
                depth++
            }

            close -> {
                depth--
                if (depth == 0) end = i
            }
        }
        i++
    }
    return end
}

/** Returns the index just past the closing quote of the string starting at [start], or null. */
private fun skipString(
    text: String,
    start: Int,
): Int? {
    var i = start + 1
    while (i < text.length) {
        when (text[i]) {
            '\\' -> i += 2
            '"' -> return i + 1
            else -> i++
        }
    }
    return null
}
