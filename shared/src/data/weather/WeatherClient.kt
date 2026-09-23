package nl.petrichor.app.data.weather

import io.ktor.client.HttpClient

/**
 * This is just the HTTP client. As this differs per platform, mainly for web, it is defined here so it can be
 * overwritten by the platform specific code.
 */
expect fun weatherHttpClient(): HttpClient
