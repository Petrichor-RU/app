package nl.petrichor.app.data.wardrobe

/**
 * Optional weather-protection level for garments (primarily outer layers such as jackets/coats).
 * Used to separate pooled rain/wind shell effects from a garment's dry baseline warmth.
 */
enum class WeatherProtection(
    val displayName: String,
    val rainProtectionScore: Double,
    val windProtectionScore: Double,
) {
    NONE(
        displayName = "None",
        rainProtectionScore = 0.0,
        windProtectionScore = 0.0,
    ),
    WIND_RESISTANT(
        displayName = "Wind-resistant",
        rainProtectionScore = 0.0,
        windProtectionScore = 1.0,
    ),
    WATER_RESISTANT(
        displayName = "Water-resistant",
        rainProtectionScore = 0.65,
        windProtectionScore = 1.0,
    ),
    WATERPROOF(
        displayName = "Waterproof",
        rainProtectionScore = 1.0,
        windProtectionScore = 1.0,
    ),
    ;

    val hasRainProtection: Boolean
        get() = rainProtectionScore > 0.0

    val hasWindProtection: Boolean
        get() = windProtectionScore > 0.0
}
