package nl.petrichor.app.data.guide

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuideRepositoryTest {
    private val place = Place("Amsterdam", Coordinates(52.37, 4.89))

    private companion object {
        const val RATE_LIMITED_BODY = """{"error":true,"reason":"Daily API request limit exceeded."}"""
    }

    private fun clientReturning(
        status: HttpStatusCode,
        body: String,
        onRequest: ((String) -> Unit)? = null,
    ): HttpClient {
        val engine =
            MockEngine { request ->
                onRequest?.invoke(request.url.toString())
                respond(body, status)
            }
        return HttpClient(engine)
    }

    @Test
    fun `successful fetch maps to a climate profile with 12 months and the attached humidity`() =
        runTest {
            val body =
                """
                {"daily": {
                  "time": ["2020-01-01", "2020-02-01"],
                  "temperature_2m_max": [5.0, 9.0],
                  "temperature_2m_min": [1.0, 4.0],
                  "precipitation_sum": [60.0, 90.0]
                }}
                """.trimIndent()
            var requestedUrl: String? = null
            val repository =
                GuideRepository(
                    ClimateClient(clientReturning(HttpStatusCode.OK, body) { requestedUrl = it }),
                    Dispatchers.Unconfined,
                )

            val profile = repository.climateFor(place, currentHumidity = 65)

            assertEquals(place, profile?.place)
            assertEquals(12, profile?.months?.size)
            val january = checkNotNull(profile?.months?.single { it.month == Months.JANUARY })
            assertEquals(5.0, january.avgHigh, 1e-9)
            assertEquals(1.0, january.avgLow, 1e-9)
            assertEquals(60.0, january.precipitationMm, 1e-9)
            assertEquals(65, january.humidity)
            val url = checkNotNull(requestedUrl)
            assertTrue(url.contains("latitude=52.37"), "url was $url")
            assertTrue(url.contains("longitude=4.89"), "url was $url")
            assertTrue(url.contains("models=era5"), "url was $url")
        }

    @Test
    fun `http error returns null silently`() =
        runTest {
            val repository =
                GuideRepository(
                    ClimateClient(clientReturning(HttpStatusCode.InternalServerError, "boom")),
                    Dispatchers.Unconfined,
                )

            assertNull(repository.climateFor(place))
        }

    @Test
    fun `malformed json returns null silently`() =
        runTest {
            val repository =
                GuideRepository(
                    ClimateClient(clientReturning(HttpStatusCode.OK, "not json")),
                    Dispatchers.Unconfined,
                )

            assertNull(repository.climateFor(place))
        }

    @Test
    fun `http 429 is retried and a later success is used`() =
        runTest {
            val body =
                """
                {"daily": {
                  "time": ["2020-01-01"],
                  "temperature_2m_max": [5.0],
                  "temperature_2m_min": [1.0],
                  "precipitation_sum": [60.0]
                }}
                """.trimIndent()
            var calls = 0
            val engine =
                MockEngine { _ ->
                    calls++
                    if (calls == 1) {
                        respond(RATE_LIMITED_BODY, HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "1"))
                    } else {
                        respond(body, HttpStatusCode.OK)
                    }
                }
            val repository = GuideRepository(ClimateClient(HttpClient(engine)), Dispatchers.Unconfined)

            val profile = repository.climateFor(place, currentHumidity = 65)

            assertEquals(2, calls)
            assertEquals(12, profile?.months?.size)
        }

    @Test
    fun `persistent http 429 returns null silently after the retries`() =
        runTest {
            var calls = 0
            val engine =
                MockEngine { _ ->
                    calls++
                    respond(RATE_LIMITED_BODY, HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "1"))
                }
            val repository = GuideRepository(ClimateClient(HttpClient(engine)), Dispatchers.Unconfined)

            assertNull(repository.climateFor(place))
            assertEquals(3, calls) // initial request plus two retries
        }

    @Test
    fun `engine failure returns null silently`() =
        runTest {
            val engine = MockEngine { throw IllegalStateException("offline") }
            val repository = GuideRepository(ClimateClient(HttpClient(engine)), Dispatchers.Unconfined)

            assertNull(repository.climateFor(place))
        }
}
