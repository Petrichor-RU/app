package nl.petrichor.app.data.guide

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MonthlyAggregationTest {
    private fun series(
        dates: List<LocalDate>,
        max: List<Double?>,
        min: List<Double?>,
        precip: List<Double?>,
    ) = DailyClimateSeries(dates, max, min, precip)

    @Test
    fun `daily values are averaged per calendar month across all years`() {
        // Two Januaries (2019, 2020) with two days each, plus one February day.
        val months =
            aggregateToMonths(
                series(
                    dates =
                        listOf(
                            LocalDate(2019, 1, 1),
                            LocalDate(2019, 1, 2),
                            LocalDate(2020, 1, 1),
                            LocalDate(2020, 1, 2),
                            LocalDate(2019, 2, 1),
                        ),
                    max = listOf(10.0, 20.0, 30.0, 40.0, 8.0),
                    min = listOf(0.0, 2.0, 4.0, 6.0, -2.0),
                    precip = listOf(10.0, 20.0, 30.0, 40.0, 50.0),
                ),
                humidity = 55,
            )

        assertEquals(12, months.size)
        val january = months.single { it.month == Months.JANUARY }
        assertEquals(25.0, january.avgHigh, 1e-9) // (10+20+30+40)/4
        assertEquals(3.0, january.avgLow, 1e-9) // (0+2+4+6)/4
        assertEquals(50.0, january.precipitationMm, 1e-9) // (10+20+30+40) / 2 years
        assertEquals(55, january.humidity)
        val february = months.single { it.month == Months.FEBRUARY }
        assertEquals(8.0, february.avgHigh, 1e-9)
        assertEquals(-2.0, february.avgLow, 1e-9)
        assertEquals(50.0, february.precipitationMm, 1e-9) // 50 / 1 year
    }

    @Test
    fun `null values are skipped when averaging`() {
        val march =
            aggregateToMonths(
                series(
                    dates = listOf(LocalDate(2020, 3, 1), LocalDate(2020, 3, 2)),
                    max = listOf(null, 20.0),
                    min = listOf(4.0, null),
                    precip = listOf(null, 30.0),
                ),
            ).single { it.month == Months.MARCH }

        assertEquals(20.0, march.avgHigh, 1e-9)
        assertEquals(4.0, march.avgLow, 1e-9)
        assertEquals(30.0, march.precipitationMm, 1e-9)
    }

    @Test
    fun `months without data are zero filled so the result always has 12 entries`() {
        val months =
            aggregateToMonths(
                series(
                    dates = listOf(LocalDate(2020, 7, 15)),
                    max = listOf(30.0),
                    min = listOf(18.0),
                    precip = listOf(5.0),
                ),
            )

        assertEquals((1..12).toList(), months.map { it.month })
        val january = months.single { it.month == Months.JANUARY }
        assertEquals(0.0, january.avgHigh, 1e-9)
        assertEquals(0.0, january.avgLow, 1e-9)
        assertEquals(0.0, january.precipitationMm, 1e-9)
        assertEquals(0, january.humidity) // unknown by default
    }

    @Test
    fun `parsing a climate API response maps the daily arrays and ignores metadata`() {
        val body =
            """
            {
              "latitude": 52.37,
              "longitude": 4.89,
              "generationtime_ms": 1.2,
              "utc_offset_seconds": 3600,
              "timezone": "Europe/Amsterdam",
              "daily_units": {"time": "iso8601", "temperature_2m_max": "°C", "precipitation_sum": "mm"},
              "daily": {
                "time": ["2020-01-01", "2020-01-02"],
                "temperature_2m_max": [5.5, 6.5],
                "temperature_2m_min": [1.0, null],
                "precipitation_sum": [2.0, 3.0]
              }
            }
            """.trimIndent()

        val series = parseClimateResponse(body)

        assertEquals(listOf(LocalDate(2020, 1, 1), LocalDate(2020, 1, 2)), series.dates)
        assertEquals(listOf(5.5, 6.5), series.maxTemps)
        assertEquals(listOf(1.0, null), series.minTemps)
        assertEquals(listOf(2.0, 3.0), series.precipitation)
    }
}
