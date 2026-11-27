package nl.petrichor.app.data.affiliate

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun affiliateHttpClientEngineFactory(): HttpClientEngineFactory<*> = Darwin
