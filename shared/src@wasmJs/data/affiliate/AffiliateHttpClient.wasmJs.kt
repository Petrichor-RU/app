package nl.petrichor.app.data.affiliate

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js

actual fun affiliateHttpClientEngineFactory(): HttpClientEngineFactory<*> = Js
