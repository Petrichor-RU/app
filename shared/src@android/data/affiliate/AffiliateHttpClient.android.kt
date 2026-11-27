package nl.petrichor.app.data.affiliate

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

actual fun affiliateHttpClientEngineFactory(): HttpClientEngineFactory<*> = CIO
