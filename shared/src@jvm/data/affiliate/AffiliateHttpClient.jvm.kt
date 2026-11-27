package nl.petrichor.app.data.affiliate

import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent

actual fun affiliateHttpClient(): HttpClient =
    HttpClient {
        install(UserAgent) { agent = BROWSER_USER_AGENT }
    }
