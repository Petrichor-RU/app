package nl.petrichor.app.data.weather

/**
 * Weather condition as documented by OpenWeatherMap's weather condition codes
 * (https://openweathermap.org/api/weather-conditions), independent of the day/night variant
 * that an icon code's trailing letter encodes.
 */
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    RAIN,
    THUNDERSTORM,
    SNOW,
    FOG,
    ;

    /** Whether this condition counts as rain for outfit purposes; thunderstorms and drizzle bring rain. */
    val isRainy: Boolean
        get() = this == RAIN || this == THUNDERSTORM
}

/**
 * Single source of truth for mapping OpenWeatherMap payloads onto a [WeatherCondition]. Each
 * `weather[]` entry in an API response carries a condition code (`id`) and an icon code (`icon`).
 * The code's group is the semantic source — e.g. 511 "freezing rain" belongs to group 5xx even
 * though it is drawn with the snow icon "13d" — so [from] prefers the code and falls back to the
 * documented icon list. Everything that branches on the weather (rain detection for outfit
 * snapshots and recommendations, the background's mood mapping) goes through here.
 */
object WeatherConditions {
    /**
     * The documented condition-code groups → condition (2xx thunderstorm, 3xx drizzle, 5xx rain,
     * 6xx snow, 7xx atmosphere, 800 clear, 80x clouds); null for unknown codes.
     */
    fun fromConditionId(conditionId: Int?): WeatherCondition? =
        when {
            conditionId == null -> null
            conditionId in 200..299 -> WeatherCondition.THUNDERSTORM
            conditionId in 300..399 || conditionId in 500..599 -> WeatherCondition.RAIN
            conditionId in 600..699 -> WeatherCondition.SNOW
            conditionId in 700..799 -> WeatherCondition.FOG
            conditionId == 800 -> WeatherCondition.CLEAR
            conditionId == 801 -> WeatherCondition.PARTLY_CLOUDY
            conditionId in 802..804 -> WeatherCondition.OVERCAST
            else -> null
        }

    private val conditionByIconPrefix: Map<String, WeatherCondition> =
        mapOf(
            "01" to WeatherCondition.CLEAR,
            "02" to WeatherCondition.PARTLY_CLOUDY,
            "03" to WeatherCondition.OVERCAST,
            "04" to WeatherCondition.OVERCAST,
            "09" to WeatherCondition.RAIN,
            "10" to WeatherCondition.RAIN,
            "11" to WeatherCondition.THUNDERSTORM,
            "13" to WeatherCondition.SNOW,
            "50" to WeatherCondition.FOG,
        )

    /** The documented icon list → condition; null when the code is missing or unknown. */
    fun fromIconCode(iconCode: String?): WeatherCondition? = conditionByIconPrefix[iconCode?.take(2)]

    /** The condition of a weather entry, preferring its condition code over its icon code. */
    fun from(
        conditionId: Int?,
        iconCode: String?,
    ): WeatherCondition? = fromConditionId(conditionId) ?: fromIconCode(iconCode)

    /**
     * Rain detection for weather snapshots and forecast days: true when the description mentions
     * rain or the condition denotes rain.
     */
    fun isRainy(
        description: String?,
        conditionId: Int?,
        iconCode: String?,
    ): Boolean = description?.contains("rain", ignoreCase = true) == true || from(conditionId, iconCode)?.isRainy == true
}
