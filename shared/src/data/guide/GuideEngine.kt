package nl.petrichor.app.data.guide

/** Meteorological seasons with their member months (Winter = Dec–Feb, Spring = Mar–May, ...). */
enum class Season(
    val displayName: String,
    val months: List<Int>,
) {
    WINTER("Winter", listOf(Months.DECEMBER, Months.JANUARY, Months.FEBRUARY)),
    SPRING("Spring", listOf(Months.MARCH, Months.APRIL, Months.MAY)),
    SUMMER("Summer", listOf(Months.JUNE, Months.JULY, Months.AUGUST)),
    AUTUMN("Autumn", listOf(Months.SEPTEMBER, Months.OCTOBER, Months.NOVEMBER)),
    ;

    companion object {
        val all: List<Season> = entries.toList()
    }
}

/** Per-season aggregates of a profile's monthly normals, shared by the rules and the UI header. */
data class SeasonSummary(
    val meanHigh: Double, // mean of the season's monthly avgHigh values, °C
    val meanLow: Double, // mean of the season's monthly avgLow values, °C
    val meanPrecipitationMm: Double, // mean of the season's monthly precipitation totals, mm
    val dailyRange: Double, // meanHigh − meanLow, the within-season spread
    val maxHumidity: Int, // highest humidity of the season's months; 0 when unknown
)

/**
 * Pure advice generator for the location guide: no I/O, no Compose imports. Rules operate on the
 * season's monthly normals; thresholds are chosen so the advice matches what the recommendation
 * engine would score highest for those temperatures (consistent with `WarmthEncoding`: jacket
 * 1.0, hoodie 0.85, sweater 0.75, t-shirt 0.30, linen 0.20). All thresholds live in this one file
 * on purpose, as tunable constants.
 */
object GuideEngine {
    private const val COLD_SEASON_HIGH_C = 5.0 // season mean high below this: deep-cold set
    private const val MILD_SEASON_HIGH_C = 12.0 // season mean high up to this: light jacket
    private const val FREEZING_LOW_C = 0.0 // season mean low below this: also a thick jacket
    private const val HOT_SEASON_HIGH_C = 26.0 // season mean high above this: breathable fabrics
    private const val VERY_HOT_SEASON_HIGH_C = 30.0 // ...and above this: also sun protection
    private const val BORDERLINE_HOT_HIGH_C = 24.0 // humidity tiebreaker zone starts here
    private const val LAYERED_RANGE_C = 8.0 // within-season daily range above this: layering
    private const val RAINY_MONTH_MM = 120.0 // mean monthly precipitation above this: rain gear
    private const val VERY_RAINY_MONTH_MM = 200.0 // ...and above this: also waterproof footwear
    private const val SNAP_DEVIATION_C = 6.0 // single-month deviation from the season mean high
    private const val HUMID_PERCENT = 70 // humidity above this is humid

    /** The profile's monthly normals belonging to [season]. */
    fun seasonMonths(
        profile: ClimateProfile,
        season: Season,
    ): List<MonthClimate> = profile.months.filter { it.month in season.months }

    /** Aggregates [profile]'s monthly normals into per-season means/spread for [season]. */
    fun seasonSummary(
        profile: ClimateProfile,
        season: Season,
    ): SeasonSummary {
        val months = seasonMonths(profile, season)
        val meanHigh = mean(months.map { it.avgHigh })
        val meanLow = mean(months.map { it.avgLow })
        return SeasonSummary(
            meanHigh = meanHigh,
            meanLow = meanLow,
            meanPrecipitationMm = mean(months.map { it.precipitationMm }),
            dailyRange = meanHigh - meanLow,
            maxHumidity = months.maxOfOrNull { it.humidity } ?: 0,
        )
    }

    private fun mean(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.average()

    /**
     * Advice kinds that hold for **every** season of [profile] — the guide's "General" section.
     * Severity-ordered like [seasonAdvice].
     */
    fun generalAdvice(profile: ClimateProfile): List<AdviceKind> =
        Season.all
            .map { seasonAdvice(profile, it) }
            .reduce { common, advice -> common.filter { it in advice } }

    /**
     * All advice kinds that apply to [season] of [profile], ordered by [AdviceKind.severity]
     * (coldest-weather items first). No truncation.
     */
    fun seasonAdvice(
        profile: ClimateProfile,
        season: Season,
    ): List<AdviceKind> {
        val summary = seasonSummary(profile, season)
        val advice = mutableListOf<AdviceKind>()
        advice += temperatureAdvice(summary)
        advice += rangeAdvice(summary)
        advice += rainAdvice(summary)
        advice += snapAdvice(seasonMonths(profile, season), summary)
        advice += humidityAdvice(summary)
        return advice.distinct().sortedBy { it.severity }
    }

    private fun temperatureAdvice(summary: SeasonSummary): List<AdviceKind> =
        buildList {
            if (summary.meanHigh < COLD_SEASON_HIGH_C) {
                addAll(listOf(AdviceKind.THERMAL_BASE_LAYER, AdviceKind.THICK_JACKET, AdviceKind.WARM_ACCESSORIES))
            } else if (summary.meanHigh <= MILD_SEASON_HIGH_C) {
                add(AdviceKind.LIGHT_JACKET)
                if (summary.meanLow < FREEZING_LOW_C) add(AdviceKind.THICK_JACKET)
            }
            if (summary.meanHigh > HOT_SEASON_HIGH_C) add(AdviceKind.BREATHABLE_FABRICS)
            if (summary.meanHigh > VERY_HOT_SEASON_HIGH_C) add(AdviceKind.SUN_PROTECTION)
        }

    private fun rangeAdvice(summary: SeasonSummary): List<AdviceKind> =
        if (summary.dailyRange > LAYERED_RANGE_C) listOf(AdviceKind.LAYERED_CLOTHING) else emptyList()

    private fun rainAdvice(summary: SeasonSummary): List<AdviceKind> =
        buildList {
            if (summary.meanPrecipitationMm > RAINY_MONTH_MM) add(AdviceKind.RAIN_GEAR)
            if (summary.meanPrecipitationMm > VERY_RAINY_MONTH_MM) add(AdviceKind.WATERPROOF_FOOTWEAR)
        }

    private fun snapAdvice(
        months: List<MonthClimate>,
        summary: SeasonSummary,
    ): List<AdviceKind> =
        buildList {
            if (months.any { it.avgHigh < summary.meanHigh - SNAP_DEVIATION_C }) add(AdviceKind.COLD_SNAP_RISK)
            if (months.any { it.avgHigh > summary.meanHigh + SNAP_DEVIATION_C }) add(AdviceKind.HEAT_SPIKE_RISK)
        }

    /**
     * Humidity only acts as a tiebreaker when the heat rule is borderline: humid air with a mean
     * high just under the hot threshold still calls for breathable fabrics. Above the hot
     * threshold the temperature rule already implies it.
     */
    private fun humidityAdvice(summary: SeasonSummary): List<AdviceKind> =
        if (summary.maxHumidity > HUMID_PERCENT && summary.meanHigh > BORDERLINE_HOT_HIGH_C) {
            listOf(AdviceKind.BREATHABLE_FABRICS)
        } else {
            emptyList()
        }
}
