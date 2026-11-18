package nl.petrichor.app.ui.background

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.lerp

/**
 * The Material3 scheme consumed by App.kt's MaterialTheme. Instead of snapping at the crossfade
 * midpoint, every colour role is lerped between [parkLightScheme] and [parkDarkScheme] over
 * [SCENE_FADE_MILLIS], in step with the park scene crossfade; only the Android system-bar icons
 * switch at the midpoint (handled in App.kt via onDarkThemeChanged).
 */
@Composable
internal fun animatedParkColorScheme(dark: Boolean): ColorScheme {
    val darkFraction by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(SCENE_FADE_MILLIS),
        label = "Park theme",
    )
    return parkColorSchemeAt(darkFraction)
}

/**
 * Pure interpolator behind [animatedParkColorScheme]: returns one of the two schemes at the
 * endpoints and a per-role [lerp] in between, covering containers, text, outlines and fixed
 * roles so no component snaps mid-transition.
 */
internal fun parkColorSchemeAt(darkFraction: Float): ColorScheme {
    if (darkFraction <= 0f || darkFraction >= 1f) {
        return if (darkFraction <= 0f) parkLightScheme else parkDarkScheme
    }
    val light = parkLightScheme
    val dark = parkDarkScheme
    // Include containers, text, outlines and fixed roles so no component snaps at the midpoint.
    return light.copy(
        primary = lerp(light.primary, dark.primary, darkFraction),
        onPrimary = lerp(light.onPrimary, dark.onPrimary, darkFraction),
        primaryContainer = lerp(light.primaryContainer, dark.primaryContainer, darkFraction),
        onPrimaryContainer = lerp(light.onPrimaryContainer, dark.onPrimaryContainer, darkFraction),
        inversePrimary = lerp(light.inversePrimary, dark.inversePrimary, darkFraction),
        secondary = lerp(light.secondary, dark.secondary, darkFraction),
        onSecondary = lerp(light.onSecondary, dark.onSecondary, darkFraction),
        secondaryContainer = lerp(light.secondaryContainer, dark.secondaryContainer, darkFraction),
        onSecondaryContainer = lerp(light.onSecondaryContainer, dark.onSecondaryContainer, darkFraction),
        tertiary = lerp(light.tertiary, dark.tertiary, darkFraction),
        onTertiary = lerp(light.onTertiary, dark.onTertiary, darkFraction),
        tertiaryContainer = lerp(light.tertiaryContainer, dark.tertiaryContainer, darkFraction),
        onTertiaryContainer = lerp(light.onTertiaryContainer, dark.onTertiaryContainer, darkFraction),
        background = lerp(light.background, dark.background, darkFraction),
        onBackground = lerp(light.onBackground, dark.onBackground, darkFraction),
        surface = lerp(light.surface, dark.surface, darkFraction),
        onSurface = lerp(light.onSurface, dark.onSurface, darkFraction),
        surfaceVariant = lerp(light.surfaceVariant, dark.surfaceVariant, darkFraction),
        onSurfaceVariant = lerp(light.onSurfaceVariant, dark.onSurfaceVariant, darkFraction),
        surfaceTint = lerp(light.surfaceTint, dark.surfaceTint, darkFraction),
        inverseSurface = lerp(light.inverseSurface, dark.inverseSurface, darkFraction),
        inverseOnSurface = lerp(light.inverseOnSurface, dark.inverseOnSurface, darkFraction),
        error = lerp(light.error, dark.error, darkFraction),
        onError = lerp(light.onError, dark.onError, darkFraction),
        errorContainer = lerp(light.errorContainer, dark.errorContainer, darkFraction),
        onErrorContainer = lerp(light.onErrorContainer, dark.onErrorContainer, darkFraction),
        outline = lerp(light.outline, dark.outline, darkFraction),
        outlineVariant = lerp(light.outlineVariant, dark.outlineVariant, darkFraction),
        scrim = lerp(light.scrim, dark.scrim, darkFraction),
        surfaceBright = lerp(light.surfaceBright, dark.surfaceBright, darkFraction),
        surfaceDim = lerp(light.surfaceDim, dark.surfaceDim, darkFraction),
        surfaceContainer = lerp(light.surfaceContainer, dark.surfaceContainer, darkFraction),
        surfaceContainerHigh = lerp(light.surfaceContainerHigh, dark.surfaceContainerHigh, darkFraction),
        surfaceContainerHighest = lerp(light.surfaceContainerHighest, dark.surfaceContainerHighest, darkFraction),
        surfaceContainerLow = lerp(light.surfaceContainerLow, dark.surfaceContainerLow, darkFraction),
        surfaceContainerLowest = lerp(light.surfaceContainerLowest, dark.surfaceContainerLowest, darkFraction),
        primaryFixed = lerp(light.primaryFixed, dark.primaryFixed, darkFraction),
        primaryFixedDim = lerp(light.primaryFixedDim, dark.primaryFixedDim, darkFraction),
        onPrimaryFixed = lerp(light.onPrimaryFixed, dark.onPrimaryFixed, darkFraction),
        onPrimaryFixedVariant = lerp(light.onPrimaryFixedVariant, dark.onPrimaryFixedVariant, darkFraction),
        secondaryFixed = lerp(light.secondaryFixed, dark.secondaryFixed, darkFraction),
        secondaryFixedDim = lerp(light.secondaryFixedDim, dark.secondaryFixedDim, darkFraction),
        onSecondaryFixed = lerp(light.onSecondaryFixed, dark.onSecondaryFixed, darkFraction),
        onSecondaryFixedVariant = lerp(light.onSecondaryFixedVariant, dark.onSecondaryFixedVariant, darkFraction),
        tertiaryFixed = lerp(light.tertiaryFixed, dark.tertiaryFixed, darkFraction),
        tertiaryFixedDim = lerp(light.tertiaryFixedDim, dark.tertiaryFixedDim, darkFraction),
        onTertiaryFixed = lerp(light.onTertiaryFixed, dark.onTertiaryFixed, darkFraction),
        onTertiaryFixedVariant = lerp(light.onTertiaryFixedVariant, dark.onTertiaryFixedVariant, darkFraction),
    )
}
