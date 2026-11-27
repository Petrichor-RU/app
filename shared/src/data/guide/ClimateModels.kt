package nl.petrichor.app.data.guide

import nl.petrichor.app.data.weather.Place

/** Calendar month numbers, 1-based. */
object Months {
    const val JANUARY = 1
    const val FEBRUARY = 2
    const val MARCH = 3
    const val APRIL = 4
    const val MAY = 5
    const val JUNE = 6
    const val JULY = 7
    const val AUGUST = 8
    const val SEPTEMBER = 9
    const val OCTOBER = 10
    const val NOVEMBER = 11
    const val DECEMBER = 12
}

/**
 * One monthly climate normal, aggregated from a daily series by [aggregateToMonths].
 *
 * @param month 1..12, see [Months]
 * @param avgHigh average of the month's daily maximum temperatures, °C
 * @param avgLow average of the month's daily minimum temperatures, °C
 * @param precipitationMm total precipitation of the month averaged over the years in the series, mm
 * @param humidity relative humidity in percent; taken from the live weather snapshot when known,
 *   else 0 (unknown) — the climate API itself offers no humidity
 */
data class MonthClimate(
    val month: Int,
    val avgHigh: Double,
    val avgLow: Double,
    val precipitationMm: Double,
    val humidity: Int,
)

/** Year-round climate profile of one [place]: exactly 12 monthly normals. */
data class ClimateProfile(
    val place: Place,
    val months: List<MonthClimate>,
)
