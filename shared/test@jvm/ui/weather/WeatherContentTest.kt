package nl.petrichor.app.ui.weather

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.ForecastDay
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import kotlin.test.Test
import kotlin.test.assertTrue

class WeatherContentTest {
    private val snapshot =
        WeatherSnapshot(
            place = Place("Amsterdam", Coordinates(52.37, 4.89)),
            temperature = 10,
            feelsLike = 8,
            description = "Cloudy",
            icon = "",
            humidity = 80,
            windSpeed = 12,
            forecast = List(5) { ForecastDay("2026-11-0${it + 1}", 5 + it, 12 + it, "") },
        )

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun `five day forecast is laid out below the current weather card even inside a box`() =
        runDesktopComposeUiTest(width = 800, height = 900) {
            setContent {
                MaterialTheme {
                    // A Box scope mimics the AnimatedContent slot the screen uses, where
                    // multiple root children would overlap at TopStart.
                    Box {
                        WeatherContent(snapshot)
                    }
                }
            }
            waitForIdle()

            val cardTop = onNodeWithText("Cloudy").fetchSemanticsNode().positionInRoot.y
            val forecastLabelTop = onNodeWithText("5-day forecast").fetchSemanticsNode().positionInRoot.y
            assertTrue(forecastLabelTop > cardTop, "forecast label at $forecastLabelTop should be below the current card at $cardTop")
        }
}
