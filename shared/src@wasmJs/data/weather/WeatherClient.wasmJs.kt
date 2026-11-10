package nl.petrichor.app.data.weather

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun weatherHttpClient(): HttpClient = HttpClient(Js)
