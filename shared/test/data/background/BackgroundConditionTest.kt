package nl.petrichor.app.data.background

import kotlinx.datetime.TimeZone
import nl.petrichor.app.data.weather.Coordinates
import nl.petrichor.app.data.weather.Place
import nl.petrichor.app.data.weather.WeatherSnapshot
import nl.petrichor.app.ui.background.BackgroundPalettes
import nl.petrichor.app.ui.background.isDark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal fun snapshot(
    icon: String = "01d",
    offset: Long = 0,
    known: Boolean = true,
) = WeatherSnapshot(
    Place("Test", Coordinates(0.0, 0.0)),
    20,
    20,
    "Clear",
    icon,
    50,
    5,
    emptyList(),
    offset,
    known,
)

class BackgroundConditionTest {
    private val now = Instant.parse("2026-11-13T12:00:00Z")

    @Test
    fun `every local hour belongs to its specified band`() {
        val expected =
            listOf(
                TimeBand.NIGHT,
                TimeBand.NIGHT,
                TimeBand.NIGHT,
                TimeBand.NIGHT,
                TimeBand.NIGHT,
                TimeBand.DAWN,
                TimeBand.DAWN,
                TimeBand.DAWN,
                TimeBand.MORNING,
                TimeBand.MORNING,
                TimeBand.MORNING,
                TimeBand.MORNING,
                TimeBand.AFTERNOON,
                TimeBand.AFTERNOON,
                TimeBand.AFTERNOON,
                TimeBand.AFTERNOON,
                TimeBand.AFTERNOON,
                TimeBand.DUSK,
                TimeBand.DUSK,
                TimeBand.EVENING,
                TimeBand.EVENING,
                TimeBand.NIGHT,
                TimeBand.NIGHT,
                TimeBand.NIGHT,
            )
        assertEquals(expected, (0..23).map(TimeBand::from))
    }

    @Test
    fun `icon mapping covers day night and unknown codes`() {
        val expected =
            mapOf(
                "01" to WeatherMood.CLEAR,
                "02" to WeatherMood.PARTLY_CLOUDY,
                "03" to WeatherMood.OVERCAST,
                "04" to WeatherMood.OVERCAST,
                "09" to WeatherMood.RAIN,
                "10" to WeatherMood.RAIN,
                "11" to WeatherMood.THUNDERSTORM,
                "13" to WeatherMood.SNOW,
                "50" to WeatherMood.FOG,
            )
        for ((prefix, mood) in expected) {
            assertEquals(mood, WeatherMood.from(prefix + "d"))
            assertEquals(mood, WeatherMood.from(prefix + "n"))
        }
        assertEquals(WeatherMood.PARTLY_CLOUDY, WeatherMood.from(""))
        assertEquals(WeatherMood.PARTLY_CLOUDY, WeatherMood.from("99n"))
    }

    @Test
    fun `offset overrides device time and crosses date boundaries`() {
        val east = BackgroundConditionResolver.resolve(snapshot("13d", 36_000), now, now, TimeZone.UTC)
        assertEquals(BackgroundCondition(TimeBand.NIGHT, WeatherMood.SNOW), east)
        val west = BackgroundConditionResolver.resolve(snapshot(offset = -43_200), now, now, TimeZone.UTC)
        assertEquals(TimeBand.NIGHT, west.band)
        val halfHour = BackgroundConditionResolver.resolve(snapshot(offset = 19_800), now, now, TimeZone.UTC)
        assertEquals(TimeBand.DUSK, halfHour.band)
    }

    @Test
    fun `missing offset uses device clock while known zero uses UTC`() {
        val device = TimeZone.of("UTC+10:00")
        assertEquals(TimeBand.NIGHT, BackgroundConditionResolver.resolve(snapshot(known = false), now, now, device).band)
        assertEquals(TimeBand.AFTERNOON, BackgroundConditionResolver.resolve(snapshot(), now, now, device).band)
        assertEquals(WeatherMood.PARTLY_CLOUDY, BackgroundConditionResolver.resolve(null, now, deviceZone = device).mood)
    }

    @Test
    fun `stale weather becomes neutral but keeps selected location time`() {
        val stale = BackgroundConditionResolver.resolve(snapshot("11d", 36_000), now, now - 1.hours, TimeZone.UTC)
        assertEquals(BackgroundCondition(TimeBand.NIGHT, WeatherMood.PARTLY_CLOUDY), stale)
    }

    @Test
    fun `all forty two palettes resolve and snow fog storm have their intended treatments`() {
        for (band in TimeBand.entries) {
            for (mood in WeatherMood.entries) {
                val palette = BackgroundPalettes.forCondition(BackgroundCondition(band, mood))
                assertEquals(mood == WeatherMood.FOG, palette.fogColor != null)
                assertTrue(palette.skyTop.alpha > 0f)
                assertTrue(palette.pathFill.alpha > 0f)
            }
            val clear = BackgroundPalettes.forCondition(BackgroundCondition(band, WeatherMood.CLEAR))
            val snow = BackgroundPalettes.forCondition(BackgroundCondition(band, WeatherMood.SNOW))
            assertNotEquals(clear.grass, snow.grass)
        }
    }

    @Test
    fun `theme rule and tab visibility match product decisions`() {
        for (band in TimeBand.entries) {
            for (mood in WeatherMood.entries) {
                assertEquals(
                    band in setOf(TimeBand.DAWN, TimeBand.DUSK, TimeBand.NIGHT) || mood == WeatherMood.THUNDERSTORM,
                    BackgroundCondition(band, mood).isDark(),
                )
            }
        }
    }
}
