@file:Suppress("MagicNumber") // Normalised drawing coordinates, particle counts and motion speeds are art constants.

package nl.petrichor.app.ui.background

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import nl.petrichor.app.data.background.BackgroundCondition
import nl.petrichor.app.data.background.WeatherMood
import kotlin.math.sin
import kotlin.random.Random

/** Duration of the condition crossfade in milliseconds; the theme lerp uses the same duration and system-bar icons switch at half of it. */
const val SCENE_FADE_MILLIS = 2500

/** Tunable particle counts from the BACKGROUND writeup, section 5.3. */
private const val RAIN_PARTICLES = 40
private const val RAIN_STREAK_X = -0.012f
private const val RAIN_STREAK_Y = 0.035f
internal const val SNOW_PARTICLES = 60

/**
 * The app background layer rendered by App.kt behind the per-tab scrim: a [ParkScene] per
 * condition with a [SCENE_FADE_MILLIS] crossfade. Owns the single frame clock shared by both
 * fading scenes, paused automatically below the STARTED lifecycle state; under
 * [LocalInspectionMode] it draws a flat colour so previews stay cheap. Draws only — it never
 * intercepts touches or pager gestures.
 */
@Composable
fun ParkBackground(
    condition: BackgroundCondition,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier.fillMaxSize().background(BackgroundPalettes.forCondition(condition).skyHorizon))
        return
    }
    var seconds by remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var previous = 0L
            while (true) {
                withFrameNanos { now ->
                    if (previous != 0L) seconds += ((now - previous) / 1_000_000_000f).coerceAtMost(0.1f)
                    previous = now
                }
            }
        }
    }
    // Both fading scenes share this clock; reading it only in drawing avoids per-frame composition.
    Crossfade(condition, modifier.fillMaxSize(), animationSpec = tween(SCENE_FADE_MILLIS), label = "Park weather") {
        ParkScene(it, timeSeconds = { seconds })
    }
}

/**
 * Draws one frame of the park scene for [condition] — sky gradient, celestial, clouds, landscape,
 * weather overlay — into a full-size Canvas. All paths and brushes are cached in [ParkGeometry]
 * keyed on palette and canvas size; [timeSeconds] only moves the procedural overlays (clouds,
 * rain, snow, fog, lightning), so animation frames allocate nothing.
 */
@Composable
fun ParkScene(
    condition: BackgroundCondition,
    modifier: Modifier = Modifier,
    timeSeconds: () -> Float = { 0f },
) {
    val palette = remember(condition) { BackgroundPalettes.forCondition(condition) }
    Canvas(
        modifier.fillMaxSize().clipToBounds().drawWithCache {
            val geometry = ParkGeometry(size, density, palette)
            val sky = Brush.verticalGradient(listOf(palette.skyTop, palette.skyHorizon))
            val sun = geometry.sunPosition(condition.band)
            val glowColor = palette.sunGlow ?: palette.moonDisc ?: Color.Transparent
            val glow = Brush.radialGradient(listOf(glowColor.copy(alpha = 0.32f), Color.Transparent), sun, geometry.unit * 0.35f)
            val fog =
                Brush.verticalGradient(
                    listOf(Color.Transparent, (palette.fogColor ?: Color.Transparent).copy(alpha = 0.5f), Color.Transparent),
                    startY = size.height * 0.32f,
                    endY = size.height * 0.88f,
                )
            val fogBrushes =
                FogBrushes(
                    fog,
                    Brush.radialGradient(
                        listOf((palette.fogColor ?: Color.Transparent).copy(alpha = 0.2f), Color.Transparent),
                        center = Offset(size.width * 0.35f, size.height * 0.58f),
                        radius = geometry.unit * 0.5f,
                    ),
                )
            val cloud = geometry.cloud
            onDrawBehind {
                val time = timeSeconds()
                drawRect(sky)
                drawCelestial(condition, palette, geometry, glow)
                drawClouds(condition.mood, palette, geometry, cloud, time)
                geometry.drawLandscape(this)
                drawWeather(condition.mood, palette, geometry, fogBrushes, time)
            }
        },
    ) {}
}

/** Sun or moon disc with its radial glow, plus the star field when the palette has a star colour. */
private fun DrawScope.drawCelestial(
    condition: BackgroundCondition,
    palette: BackgroundPalette,
    geometry: ParkGeometry,
    glow: Brush,
) {
    val position = geometry.sunPosition(condition.band)
    if (palette.sunDisc != null || palette.moonDisc != null) {
        drawCircle(glow, geometry.unit * 0.35f, position)
        drawCircle(palette.sunDisc ?: palette.moonDisc!!, geometry.unit * 0.052f, position)
    }
    palette.starColor?.let { color ->
        val count = if (condition.mood == WeatherMood.CLEAR) 32 else 12
        for (i in 0 until count) {
            drawCircle(color.copy(alpha = 0.4f + (i % 4) * 0.15f), geometry.unit * 0.0018f, geometry.stars[i])
        }
    }
}

/** 2-5 slowly drifting clouds depending on mood; count, colour and alpha carry the condition. */
private fun DrawScope.drawClouds(
    mood: WeatherMood,
    palette: BackgroundPalette,
    geometry: ParkGeometry,
    cloud: androidx.compose.ui.graphics.Path,
    time: Float,
) {
    val count =
        when (mood) {
            WeatherMood.CLEAR -> 2
            WeatherMood.FOG -> 2
            WeatherMood.PARTLY_CLOUDY -> 3
            else -> 5
        }
    for (i in 0 until count) {
        val travel = size.width + geometry.unit * 0.6f
        val x = ((i * travel / count + time * geometry.unit * 0.003f) % travel) - geometry.unit * 0.3f
        val y = size.height * (0.13f + (i % 3) * 0.085f)
        translate(x, y) {
            val cloudColor = if (i % 2 == 0) palette.cloudLight else palette.cloudDark
            drawPath(cloud, cloudColor, alpha = if (mood == WeatherMood.CLEAR) 0.38f else 0.85f)
        }
    }
}

/**
 * Procedural weather overlay for the mood, advanced by [time]: rain streaks (plus seeded
 * lightning pulses for thunderstorm), swaying snowflakes, or the slowly creeping fog veil.
 */
private fun DrawScope.drawWeather(
    mood: WeatherMood,
    palette: BackgroundPalette,
    geometry: ParkGeometry,
    fog: FogBrushes,
    time: Float,
) {
    when (mood) {
        WeatherMood.RAIN, WeatherMood.THUNDERSTORM -> {
            for (i in 0 until RAIN_PARTICLES) {
                val seed = geometry.particles[i]
                val fallDistance = time * (0.5f + seed.x * 0.3f) * size.height
                val y = (seed.y * size.height + fallDistance) % size.height
                // Derive horizontal travel from the same pixel-space direction as the streak.
                // Normalise negative remainders so drops wrap correctly while moving left.
                val horizontalTravel = seed.x * size.width + fallDistance * (RAIN_STREAK_X / RAIN_STREAK_Y)
                val x = ((horizontalTravel % size.width) + size.width) % size.width
                drawLine(
                    palette.cloudLight.copy(alpha = 0.4f),
                    Offset(x, y),
                    Offset(x + geometry.unit * RAIN_STREAK_X, y + geometry.unit * RAIN_STREAK_Y),
                    geometry.unit * 0.0017f,
                )
            }
            if (mood == WeatherMood.THUNDERSTORM) {
                val cycle = (time / 12f).toInt()
                val phase = time % 12f
                val pulseStart = geometry.flashOffsets[cycle % geometry.flashOffsets.size]
                val pulse = (1f - kotlin.math.abs(phase - pulseStart) / 0.1f).coerceIn(0f, 1f)
                drawRect(Color(0xFFBBC7DF).copy(alpha = pulse * 0.16f), size = Size(size.width, size.height * 0.65f))
            }
        }

        WeatherMood.SNOW -> {
            for (i in 0 until SNOW_PARTICLES) {
                val seed = geometry.particles[i]
                val x = seed.x * size.width + sin(time * 0.4f + i) * geometry.unit * 0.025f
                val y = ((seed.y + time * (0.018f + seed.x * 0.025f)) % 1f) * size.height
                drawCircle(palette.cloudLight, geometry.unit * (0.002f + seed.x * 0.004f), Offset(x, y))
            }
        }

        WeatherMood.FOG -> {
            translate(sin(time / 15f) * geometry.unit * 0.025f) {
                drawRect(
                    fog.veil,
                    topLeft = Offset(-geometry.unit * 0.03f, 0f),
                    size = Size(size.width + geometry.unit * 0.06f, size.height),
                    alpha =
                        0.65f + sin(time * (2f * kotlin.math.PI.toFloat() / 30f)) * 0.15f,
                )
                drawRect(fog.patch)
            }
        }

        else -> {
            return
        }
    }
}

/** Fog veil gradient across the mid-ground plus a soft patch, both cached with the palette. */
private data class FogBrushes(
    val veil: Brush,
    val patch: Brush,
)

/** Deterministic random points in 0..1 space, used as particle seeds so the field is stable across frames. */
internal fun sceneParticles(count: Int): List<Offset> {
    val random = Random(73)
    return List(count) { Offset(random.nextFloat(), random.nextFloat()) }
}
