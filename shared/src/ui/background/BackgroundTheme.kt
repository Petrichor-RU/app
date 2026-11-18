package nl.petrichor.app.ui.background

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import nl.petrichor.app.data.background.BackgroundCondition
import nl.petrichor.app.data.background.TimeBand
import nl.petrichor.app.data.background.WeatherMood

// Binding simple rule from section 6.2: rain alone doesn't force a daytime dark scheme.
private val darkBands = setOf(TimeBand.DAWN, TimeBand.DUSK, TimeBand.NIGHT)

/**
 * Binding dark-scheme rule from section 6.2 of the BACKGROUND writeup: DAWN, DUSK, NIGHT and
 * THUNDERSTORM are always dark; rain alone in daylight stays light. Consumed by App.kt to pick
 * the animated scheme and to time the Android system-bar icon switch at the crossfade midpoint.
 */
fun BackgroundCondition.isDark(): Boolean = band in darkBands || mood == WeatherMood.THUNDERSTORM

/**
 * Light Material3 scheme applied by App.kt's MaterialTheme while the sky is light. The full
 * surfaceContainer scale is set explicitly so filled cards follow the scene tint instead of
 * Material's default colours; see [parkDarkScheme] for the dark counterpart.
 */
val parkLightScheme =
    lightColorScheme(
        primary = Color(0xFF426754),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC4ECD0),
        onPrimaryContainer = Color(0xFF102E20),
        secondary = Color(0xFF56645A),
        secondaryContainer = Color(0xFFD9E8DB),
        tertiary = Color(0xFF526571),
        background = Color(0xFFF6F8F3),
        surface = Color(0xFFF6F8F3),
        // Filled cards use surfaceContainerHighest, not surface or primary.
        // Define the full surface scale so components don't retain Material's default tint.
        surfaceDim = Color(0xFFD6DED6),
        surfaceBright = Color(0xFFF6F8F3),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F4ED),
        surfaceContainer = Color(0xFFEAF0E7),
        surfaceContainerHigh = Color(0xFFE4EBE1),
        surfaceContainerHighest = Color(0xFFDDE5DC),
        onSurface = Color(0xFF19201B),
        surfaceVariant = Color(0xFFDDE5DC),
        onSurfaceVariant = Color(0xFF414A43),
    )

/** Dark scheme for night/dusk/dawn/thunderstorm conditions; picked via [isDark]. */
val parkDarkScheme =
    darkColorScheme(
        primary = Color(0xFFA7D1B4),
        onPrimary = Color(0xFF123825),
        primaryContainer = Color(0xFF2B513D),
        onPrimaryContainer = Color(0xFFC4ECD0),
        secondary = Color(0xFFBDCCBF),
        secondaryContainer = Color(0xFF3C4B40),
        tertiary = Color(0xFFB3CBD9),
        background = Color(0xFF111914),
        surface = Color(0xFF16201B),
        surfaceDim = Color(0xFF111914),
        surfaceBright = Color(0xFF354039),
        surfaceContainerLowest = Color(0xFF0C140F),
        surfaceContainerLow = Color(0xFF18231C),
        surfaceContainer = Color(0xFF1D2822),
        surfaceContainerHigh = Color(0xFF27332B),
        surfaceContainerHighest = Color(0xFF323E35),
        onSurface = Color(0xFFE0E9E1),
        surfaceVariant = Color(0xFF3F4A42),
        onSurfaceVariant = Color(0xFFC0CCC1),
    )

/**
 * Per-tab scrim alpha applied in App.kt between the park scene and the tab content, indexed by
 * tab order (Wardrobe, Journal, Suggestions, Weather): the scene stays most visible on the
 * Weather tab and is dimmed most on Wardrobe so grid text stays readable. Tunable constants;
 * asserted against the product decisions in BackgroundConditionTest.
 */
val tabScrimAlphas = listOf(0.60f, 0.50f, 0.35f, 0.10f)
