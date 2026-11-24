package nl.petrichor.app.data.background

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nl.petrichor.app.data.weather.WeatherCondition
import nl.petrichor.app.data.weather.WeatherConditions
import nl.petrichor.app.data.weather.WeatherSnapshot
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Tunable local-hour boundaries; each band includes its start and excludes the next.
private const val DAWN_START = 5
private const val MORNING_START = 8
private const val AFTERNOON_START = 12
private const val DUSK_START = 17
private const val EVENING_START = 19
private const val NIGHT_START = 21

/**
 * Time-of-day band driving the park background, in local hours of the selected weather location
 * (inclusive start, exclusive end). The boundaries below are the tunable, owner-approved
 * constants from the BACKGROUND writeup, section 3.1.
 */
enum class TimeBand {
    DAWN,
    MORNING,
    AFTERNOON,
    DUSK,
    EVENING,
    NIGHT,
    ;

    companion object {
        /** Pure mapping from a local hour (0..23) to its band; boundary-tested for all 24 hours. */
        fun from(localHour: Int): TimeBand {
            require(localHour in 0..23)
            return when (localHour) {
                in DAWN_START until MORNING_START -> DAWN
                in MORNING_START until AFTERNOON_START -> MORNING
                in AFTERNOON_START until DUSK_START -> AFTERNOON
                in DUSK_START until EVENING_START -> DUSK
                in EVENING_START until NIGHT_START -> EVENING
                else -> NIGHT
            }
        }
    }
}

/**
 * Weather mood of the background, mapped from the current snapshot's weather via the canonical
 * [WeatherConditions] mapping. Unknown codes fall back to [PARTLY_CLOUDY].
 */
enum class WeatherMood {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    RAIN,
    THUNDERSTORM,
    SNOW,
    FOG,
    ;

    companion object {
        /** Pure mapping from a weather entry's condition code and icon code (e.g. 500, "10n") to its mood. */
        fun from(
            conditionId: Int?,
            iconCode: String?,
        ): WeatherMood =
            when (WeatherConditions.from(conditionId, iconCode)) {
                WeatherCondition.CLEAR -> CLEAR
                WeatherCondition.PARTLY_CLOUDY -> PARTLY_CLOUDY
                WeatherCondition.OVERCAST -> OVERCAST
                WeatherCondition.RAIN -> RAIN
                WeatherCondition.THUNDERSTORM -> THUNDERSTORM
                WeatherCondition.SNOW -> SNOW
                WeatherCondition.FOG -> FOG
                null -> PARTLY_CLOUDY
            }
    }
}

/**
 * The background's current situation: one [TimeBand] x one [WeatherMood]. This is the single
 * input to palette lookup and scene rendering in the ui.background package, and to the
 * light/dark theme rule; it is produced by [BackgroundConditionResolver] from the shared
 * weather state and the clock.
 */
data class BackgroundCondition(
    val band: TimeBand,
    val mood: WeatherMood,
)

/**
 * Pure final step of the background-condition pipeline: combines the shared weather snapshot
 * (and its load time) with a clock to produce the [BackgroundCondition] consumed by the scene
 * and the theme. No I/O here; the 60 s ticker that re-triggers this lives in the app-shell
 * background controller.
 */
object BackgroundConditionResolver {
    /** Tunable freshness policy: older weather loses its mood but retains the location's offset. */
    val maxWeatherAge = 1.hours

    /**
     * Resolves the condition for [now]. Local time comes from the snapshot's UTC offset when
     * [WeatherSnapshot.utcOffsetKnown], otherwise from [deviceZone]. Fresh weather contributes
     * its condition's mood; missing or stale weather falls back to [WeatherMood.PARTLY_CLOUDY].
     */
    fun resolve(
        weather: WeatherSnapshot?,
        now: Instant,
        loadedAt: Instant? = null,
        deviceZone: TimeZone = TimeZone.currentSystemDefault(),
    ): BackgroundCondition {
        val localTime =
            if (weather?.utcOffsetKnown == true) {
                (now + weather.utcOffsetSeconds.seconds).toLocalDateTime(TimeZone.UTC)
            } else {
                now.toLocalDateTime(deviceZone)
            }
        val fresh = loadedAt != null && now - loadedAt < maxWeatherAge
        return BackgroundCondition(
            TimeBand.from(localTime.hour),
            if (weather != null && fresh) WeatherMood.from(weather.conditionId, weather.icon) else WeatherMood.PARTLY_CLOUDY,
        )
    }
}
