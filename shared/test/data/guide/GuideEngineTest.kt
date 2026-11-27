package nl.petrichor.app.data.guide

import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuideEngineTest {
    private fun month(
        month: Int,
        high: Double,
        low: Double,
        precip: Double = 0.0,
        humidity: Int = 0,
    ) = MonthClimate(month, high, low, precip, humidity)

    /** A profile whose every month has the given normals, with single months overridable. */
    private fun profile(
        high: Double,
        low: Double,
        precip: Double = 0.0,
        humidity: Int = 0,
        overrides: Map<Int, MonthClimate> = emptyMap(),
    ) = ClimateProfile(
        place = Place("Testville", Coordinates(0.0, 0.0)),
        months = (1..12).map { m -> overrides[m] ?: month(m, high, low, precip, humidity) },
    )

    @Test
    fun `tropical hot humid season gets breathable fabrics and sun protection`() {
        // Singapore-style: hot all year, humid.
        val advice = GuideEngine.seasonAdvice(profile(high = 31.0, low = 25.0, humidity = 80), Season.SUMMER)

        assertEquals(listOf(AdviceKind.BREATHABLE_FABRICS, AdviceKind.SUN_PROTECTION), advice)
    }

    @Test
    fun `maritime mild wet season with a wide daily range gets layering and rain gear`() {
        // Lisbon-style: mild, wet, big day/night spread.
        val advice = GuideEngine.seasonAdvice(profile(high = 18.0, low = 9.0, precip = 130.0), Season.AUTUMN)

        assertEquals(listOf(AdviceKind.LAYERED_CLOTHING, AdviceKind.RAIN_GEAR), advice)
    }

    @Test
    fun `cold continental winter gets the deep cold set`() {
        // Helsinki-style winter.
        val advice = GuideEngine.seasonAdvice(profile(high = -5.0, low = -12.0), Season.WINTER)

        assertEquals(
            listOf(AdviceKind.THERMAL_BASE_LAYER, AdviceKind.THICK_JACKET, AdviceKind.WARM_ACCESSORIES),
            advice,
        )
    }

    @Test
    fun `desert summer with a spiky range and a heat spike gets layered breathable sun and spike advice`() {
        val desert =
            profile(
                high = 38.0,
                low = 20.0,
                overrides =
                    mapOf(
                        Months.JUNE to month(Months.JUNE, 38.0, 20.0),
                        Months.JULY to month(Months.JULY, 40.0, 20.0),
                        Months.AUGUST to month(Months.AUGUST, 49.0, 20.0),
                    ),
            )

        val advice = GuideEngine.seasonAdvice(desert, Season.SUMMER)

        assertEquals(
            listOf(
                AdviceKind.LAYERED_CLOTHING,
                AdviceKind.BREATHABLE_FABRICS,
                AdviceKind.SUN_PROTECTION,
                AdviceKind.HEAT_SPIKE_RISK,
            ),
            advice,
        )
    }

    @Test
    fun `winter groups december january and february only`() {
        // Mild all year except a cold Dec–Feb: only winter sees the cold.
        val p =
            profile(
                high = 15.0,
                low = 10.0,
                overrides =
                    mapOf(
                        Months.DECEMBER to month(Months.DECEMBER, 6.0, 1.0),
                        Months.JANUARY to month(Months.JANUARY, -2.0, -6.0),
                        Months.FEBRUARY to month(Months.FEBRUARY, 6.0, 1.0),
                    ),
            )

        assertEquals(
            listOf(AdviceKind.THERMAL_BASE_LAYER, AdviceKind.THICK_JACKET, AdviceKind.WARM_ACCESSORIES),
            GuideEngine.seasonAdvice(p, Season.WINTER),
        )
        assertTrue(GuideEngine.seasonAdvice(p, Season.SPRING).isEmpty())
        assertTrue(GuideEngine.seasonAdvice(p, Season.SUMMER).isEmpty())
    }

    @Test
    fun `advice is ordered coldest weather first and never truncated`() {
        // Cold, wet, wide-range winter: six kinds all apply and keep severity order.
        val advice = GuideEngine.seasonAdvice(profile(high = 2.0, low = -8.0, precip = 210.0), Season.WINTER)

        assertEquals(
            listOf(
                AdviceKind.THERMAL_BASE_LAYER,
                AdviceKind.THICK_JACKET,
                AdviceKind.WARM_ACCESSORIES,
                AdviceKind.LAYERED_CLOTHING,
                AdviceKind.WATERPROOF_FOOTWEAR,
                AdviceKind.RAIN_GEAR,
            ),
            advice,
        )
    }

    @Test
    fun `season summary aggregates the member months`() {
        val summary = GuideEngine.seasonSummary(profile(high = 20.0, low = 10.0, precip = 90.0, humidity = 60), Season.SUMMER)

        assertEquals(20.0, summary.meanHigh, 1e-9)
        assertEquals(10.0, summary.meanLow, 1e-9)
        assertEquals(10.0, summary.dailyRange, 1e-9)
        assertEquals(90.0, summary.meanPrecipitationMm, 1e-9)
        assertEquals(60, summary.maxHumidity)
    }

    @Test
    fun `mean high exactly at the cold threshold gets a light jacket and not the deep cold set`() {
        assertEquals(listOf(AdviceKind.LIGHT_JACKET), GuideEngine.seasonAdvice(profile(high = 5.0, low = 2.0), Season.SPRING))
        assertEquals(
            listOf(AdviceKind.THERMAL_BASE_LAYER, AdviceKind.THICK_JACKET, AdviceKind.WARM_ACCESSORIES),
            GuideEngine.seasonAdvice(profile(high = 4.9, low = 2.0), Season.SPRING),
        )
    }

    @Test
    fun `mean high in the gap above twelve gets no temperature advice`() {
        assertEquals(listOf(AdviceKind.LIGHT_JACKET), GuideEngine.seasonAdvice(profile(high = 12.0, low = 8.0), Season.SPRING))
        assertTrue(GuideEngine.seasonAdvice(profile(high = 12.1, low = 8.0), Season.SPRING).isEmpty())
    }

    @Test
    fun `mean low below freezing adds a thick jacket to the light one`() {
        val advice = GuideEngine.seasonAdvice(profile(high = 7.0, low = -0.5), Season.AUTUMN)

        assertEquals(listOf(AdviceKind.THICK_JACKET, AdviceKind.LIGHT_JACKET), advice)
    }

    @Test
    fun `mean high above 26 gets breathable fabrics and exactly 26 does not`() {
        assertTrue(GuideEngine.seasonAdvice(profile(high = 26.0, low = 20.0), Season.SUMMER).isEmpty())
        assertEquals(listOf(AdviceKind.BREATHABLE_FABRICS), GuideEngine.seasonAdvice(profile(high = 26.1, low = 20.0), Season.SUMMER))
    }

    @Test
    fun `mean high above 30 adds sun protection`() {
        assertEquals(
            listOf(AdviceKind.BREATHABLE_FABRICS, AdviceKind.SUN_PROTECTION),
            GuideEngine.seasonAdvice(profile(high = 30.1, low = 24.0), Season.SUMMER),
        )
    }

    @Test
    fun `daily range above 8 gets layering and exactly 8 does not`() {
        assertTrue(GuideEngine.seasonAdvice(profile(high = 20.0, low = 12.0), Season.AUTUMN).isEmpty())
        assertEquals(listOf(AdviceKind.LAYERED_CLOTHING), GuideEngine.seasonAdvice(profile(high = 20.0, low = 11.9), Season.AUTUMN))
    }

    @Test
    fun `mean precipitation above 120 gets rain gear and exactly 120 does not`() {
        assertTrue(GuideEngine.seasonAdvice(profile(high = 18.0, low = 12.0, precip = 120.0), Season.AUTUMN).isEmpty())
        assertEquals(
            listOf(AdviceKind.RAIN_GEAR),
            GuideEngine.seasonAdvice(profile(high = 18.0, low = 12.0, precip = 120.1), Season.AUTUMN),
        )
    }

    @Test
    fun `mean precipitation above 200 adds waterproof footwear`() {
        val advice = GuideEngine.seasonAdvice(profile(high = 18.0, low = 12.0, precip = 200.1), Season.AUTUMN)

        assertEquals(listOf(AdviceKind.WATERPROOF_FOOTWEAR, AdviceKind.RAIN_GEAR), advice)
    }

    @Test
    fun `single month deviation above 6 gets the matching snap advice and exactly 6 does not`() {
        val heatSpike =
            profile(
                high = 10.0,
                low = 10.0,
                overrides =
                    mapOf(
                        Months.MARCH to month(Months.MARCH, 10.0, 10.0),
                        Months.APRIL to month(Months.APRIL, 10.0, 10.0),
                        Months.MAY to month(Months.MAY, 19.1, 10.0),
                    ),
            )
        val noSpike =
            profile(
                high = 10.0,
                low = 10.0,
                overrides =
                    mapOf(
                        Months.MARCH to month(Months.MARCH, 10.0, 10.0),
                        Months.APRIL to month(Months.APRIL, 10.0, 10.0),
                        Months.MAY to month(Months.MAY, 19.0, 10.0),
                    ),
            )
        val coldSnap =
            profile(
                high = 19.0,
                low = 10.0,
                overrides =
                    mapOf(
                        Months.SEPTEMBER to month(Months.SEPTEMBER, 19.0, 10.0),
                        Months.OCTOBER to month(Months.OCTOBER, 19.0, 10.0),
                        Months.NOVEMBER to month(Months.NOVEMBER, 9.9, 10.0),
                    ),
            )

        assertEquals(listOf(AdviceKind.HEAT_SPIKE_RISK), GuideEngine.seasonAdvice(heatSpike, Season.SPRING))
        assertTrue(GuideEngine.seasonAdvice(noSpike, Season.SPRING).isEmpty())
        assertEquals(listOf(AdviceKind.COLD_SNAP_RISK), GuideEngine.seasonAdvice(coldSnap, Season.AUTUMN))
    }

    @Test
    fun `general advice contains only kinds that occur in all four seasons`() {
        // Mild, wet, wide-range all year: layering and rain gear hold everywhere.
        val p = profile(high = 18.0, low = 9.0, precip = 130.0)

        assertEquals(listOf(AdviceKind.LAYERED_CLOTHING, AdviceKind.RAIN_GEAR), GuideEngine.generalAdvice(p))
    }

    @Test
    fun `general advice excludes season-specific kinds`() {
        // Freezing winter, mild rest of the year: the deep-cold set is winter-only.
        val p =
            profile(
                high = 15.0,
                low = 10.0,
                overrides =
                    mapOf(
                        Months.DECEMBER to month(Months.DECEMBER, 6.0, 1.0),
                        Months.JANUARY to month(Months.JANUARY, -2.0, -6.0),
                        Months.FEBRUARY to month(Months.FEBRUARY, 6.0, 1.0),
                    ),
            )

        assertTrue(GuideEngine.generalAdvice(p).isEmpty())
    }

    @Test
    fun `humid borderline heat gets breathable fabrics as a tiebreaker`() {
        // Mean high 25 sits just under the hot threshold: only humidity pushes it over.
        assertEquals(
            listOf(AdviceKind.BREATHABLE_FABRICS),
            GuideEngine.seasonAdvice(profile(high = 25.0, low = 18.0, humidity = 71), Season.SUMMER),
        )
        assertTrue(GuideEngine.seasonAdvice(profile(high = 25.0, low = 18.0, humidity = 70), Season.SUMMER).isEmpty())
        assertTrue(GuideEngine.seasonAdvice(profile(high = 25.0, low = 18.0, humidity = 0), Season.SUMMER).isEmpty())
        assertTrue(GuideEngine.seasonAdvice(profile(high = 23.9, low = 18.0, humidity = 80), Season.SUMMER).isEmpty())
    }
}
