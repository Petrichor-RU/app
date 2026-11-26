package nl.petrichor.app.data.affiliate

import nl.petrichor.app.data.wardrobe.ClothingSupertype
import nl.petrichor.app.data.weather.WeatherConditions
import nl.petrichor.app.data.weather.WeatherSnapshot

/**
 * The store the affiliate feed uses; revealed on the detail screen, not on the card.
 * [supportedCountries] gates the sponsored items: only users whose resolved country is in the
 * set see this store's recommendations.
 */
enum class AffiliateStore(
    val displayName: String,
    val supportedCountries: Set<String>,
) {
    FLIPKART("Flipkart", setOf("IN")),
}

/**
 * One sponsored product. Never a Room entity and never enters WardrobeRepository; it lives only
 * in the affiliate state adjacent to the wardrobe. [url] is always a ready-to-go https affiliate
 * link (the adapter injected the affiliate code at build time — open it as-is);
 * [typeName]/[supertype] map the store's taxonomy onto the built-in clothing types so the
 * wardrobe type filter applies to sponsored cards like to the user's own items.
 */
data class AffiliateItem(
    val id: String,
    val name: String,
    val store: AffiliateStore,
    val url: String,
    val imageUrl: String?,
    /** Display-ready price (e.g. "€12.00"); shown on the detail screen only, never on the card. */
    val price: String?,
    val description: String?,
    val typeName: String,
    val supertype: ClothingSupertype,
)

enum class TemperatureBand {
    WARM,
    MILD,
    COOL,
    COLD,
    ;

    /** True when this band is colder than [other] (COLD is the coldest band). */
    fun isColderThan(other: TemperatureBand): Boolean = ordinal > other.ordinal

    companion object {
        // Default band mapping; bound as constants so tuning is one line.
        const val WARM_MIN_CELSIUS = 22
        const val MILD_MIN_CELSIUS = 15
        const val COOL_MIN_CELSIUS = 8

        fun fromCelsius(celsius: Int): TemperatureBand =
            when {
                celsius >= WARM_MIN_CELSIUS -> WARM
                celsius >= MILD_MIN_CELSIUS -> MILD
                celsius >= COOL_MIN_CELSIUS -> COOL
                else -> COLD
            }
    }
}

/**
 * What to shop for, derived from the 5-day forecast of the selected weather location.
 * [primaryTypeNames] drive the adapter queries; [fillerTypeNames] (accessories/headwear/footwear)
 * may always fill unused slots.
 */
data class ShoppingContext(
    val rainExpected: Boolean,
    val temperatureBand: TemperatureBand,
) {
    val primaryTypeNames: List<String>
        get() =
            when {
                temperatureBand.isColderThan(TemperatureBand.MILD) && rainExpected -> {
                    // Rain-appropriate: jackets and long pants; footwear comes from the fillers.
                    listOf("Jackets", "Pants", "Jeans")
                }

                temperatureBand.isColderThan(TemperatureBand.MILD) -> {
                    listOf("Hoodies", "Sweaters", "Jackets")
                }

                temperatureBand == TemperatureBand.MILD -> {
                    listOf("Shirts", "Sweaters")
                }

                rainExpected -> {
                    listOf("T-Shirts", "Pants")
                }

                else -> {
                    listOf("T-Shirts", "Shorts")
                }
            }

    val fillerTypeNames: List<String>
        get() = listOf("Sneakers", "Caps", "Ties")
}

/**
 * Derives the [ShoppingContext] from the full 5-day forecast plus current conditions of the
 * selected location. A null/failed weather snapshot degrades to the neutral default used by the
 * weather tab (not rainy, MILD band) — never crash, never block the wardrobe.
 */
fun deriveShoppingContext(weather: WeatherSnapshot?): ShoppingContext {
    if (weather == null) {
        return ShoppingContext(rainExpected = false, temperatureBand = TemperatureBand.MILD)
    }
    val rainExpected =
        WeatherConditions.isRainy(weather.description, weather.conditionId, weather.icon) ||
            weather.forecast.any { WeatherConditions.from(it.conditionId, it.icon)?.isRainy == true }
    // The band comes from the coldest daily minimum alongside the current temperature; the
    // colder of the two wins, so a cold snap days out still surfaces warm layers.
    val forecastMin = weather.forecast.minOfOrNull { it.min }
    val bands =
        listOfNotNull(
            forecastMin?.let { TemperatureBand.fromCelsius(it) },
            TemperatureBand.fromCelsius(weather.temperature),
        )
    val band = bands.maxByOrNull { it.ordinal } ?: TemperatureBand.MILD
    return ShoppingContext(rainExpected = rainExpected, temperatureBand = band)
}
