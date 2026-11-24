package nl.petrichor.app.data.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WeatherRepositoryTest {
    private val tokyo = Place("Tokyo", Coordinates(35.6762, 139.6503))

    @Test
    fun `groups 3-hour forecasts by local calendar date across UTC midnight boundaries`() {
        val repository = WeatherRepository(apiKey = "test-key")
        val snapshot = repository.parseWeatherSnapshot(tokyo, currentWeatherJson(), multiDayForecastJson(), capturedAtEpochMs = 123456L)

        assertEquals(32400L, snapshot.utcOffsetSeconds)
        assertTrue(snapshot.utcOffsetKnown)
        assertEquals(14, snapshot.temperature)
        assertEquals(500, snapshot.conditionId)
        assertEquals(14.4, snapshot.temperatureC)
        assertEquals(4.2, snapshot.windSpeedMs)
        assertEquals(1.5, snapshot.rainVolumeMm)
        assertEquals(123456L, snapshot.capturedAtEpochMs)

        assertEquals(2, snapshot.forecast.size)
        val day1 = snapshot.forecast[0]
        assertEquals("2026-11-12", day1.label)
        assertEquals(13, day1.min)
        assertEquals(15, day1.max)
        assertEquals(800, day1.conditionId)

        val day2 = snapshot.forecast[1]
        assertEquals("2026-11-13", day2.label)
        // True daily min includes the 03:00 JST (18:00 UTC previous day) reading of 3.0 °C.
        assertEquals(3, day2.min)
        assertEquals(22, day2.max)
        // Daytime (06:00-22:00 JST) averages exclude the 03:00 JST wind=10 and feelsLike=2!
        assertEquals(18.0, assertNotNull(day2.feelsLikeC), 1e-9)
        assertEquals(5.0, assertNotNull(day2.windSpeedMs), 1e-9)
        assertEquals(55, day2.humidity)
        assertEquals(0.85, assertNotNull(day2.precipitationProbability), 1e-9)
        assertEquals(3.0, assertNotNull(day2.rainVolumeMm), 1e-9)
        // Noon daytime icon and condition code (12:00 JST) are chosen over 03:00 JST night entry.
        assertEquals("10d", day2.icon)
        assertEquals(501, day2.conditionId)
    }

    private fun currentWeatherJson(): String =
        """
        {
          "dt": 1794484800,
          "weather": [{"id": 500, "description": "light rain", "icon": "10n"}],
          "main": {"temp": 14.4, "feels_like": 13.1, "humidity": 78},
          "wind": {"speed": 4.2},
          "rain": {"1h": 1.5}
        }
        """.trimIndent()

    // UTC+9 (32400s): 12:00 UTC = 21:00 JST (day 1), 18:00 UTC = 03:00 JST (day 2 night),
    // 03:00 UTC = 12:00 JST (day 2 noon), 06:00 UTC = 15:00 JST (day 2 afternoon).
    private fun multiDayForecastJson(): String =
        """
        {
          "city": {"timezone": 32400, "name": "Tokyo"},
          "list": [
            {
              "dt": 1794484800,
              "main": {"temp": 14.0, "feels_like": 13.0, "temp_min": 13.0, "temp_max": 15.0, "humidity": 75},
              "weather": [{"id": 800, "description": "clear sky", "icon": "01n"}],
              "wind": {"speed": 3.0},
              "pop": 0.1
            },
            {
              "dt": 1794506400,
              "main": {"temp": 4.0, "feels_like": 2.0, "temp_min": 3.0, "temp_max": 5.0, "humidity": 90},
              "weather": [{"id": 800, "description": "clear sky", "icon": "01n"}],
              "wind": {"speed": 10.0},
              "pop": 0.0
            },
            {
              "dt": 1794538800,
              "main": {"temp": 18.0, "feels_like": 17.0, "temp_min": 17.0, "temp_max": 19.0, "humidity": 60},
              "weather": [{"id": 501, "description": "moderate rain", "icon": "10d"}],
              "wind": {"speed": 4.0},
              "pop": 0.85,
              "rain": {"3h": 2.4}
            },
            {
              "dt": 1794549600,
              "main": {"temp": 20.0, "feels_like": 19.0, "temp_min": 19.0, "temp_max": 22.0, "humidity": 50},
              "weather": [{"id": 801, "description": "few clouds", "icon": "02d"}],
              "wind": {"speed": 6.0},
              "pop": 0.3,
              "rain": {"3h": 0.6}
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `falls back to all intervals when a partial day only has night readings`() {
        // UTC+0: single entry at 2026-11-12 03:00 UTC
        val items =
            listOf(
                OpenWeatherForecastItemDto(
                    dt = 1794452400L,
                    main = OpenWeatherMainDto(temp = 6.0, feelsLike = 4.0, tempMin = 5.0, tempMax = 7.0, humidity = 80),
                    weather = listOf(OpenWeatherConditionDto(id = 802, description = "clouds", icon = "03n")),
                    wind = OpenWeatherWindDto(speed = 3.5),
                    pop = 0.2,
                ),
            )

        val days = groupForecastByLocalDate(items, utcOffsetSeconds = 0L)

        assertEquals(1, days.size)
        val day = days.single()
        assertEquals("2026-11-12", day.label)
        assertEquals(4.0, assertNotNull(day.feelsLikeC), 1e-9)
        assertEquals(3.5, assertNotNull(day.windSpeedMs), 1e-9)
        assertEquals("03n", day.icon)
        assertEquals(802, day.conditionId)
    }
}
