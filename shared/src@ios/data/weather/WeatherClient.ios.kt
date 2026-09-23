package nl.petrichor.app.data.weather

import io.ktor.client.HttpClient

actual fun weatherHttpClient(): HttpClient = HttpClient()
