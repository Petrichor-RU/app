@file:Suppress("MagicNumber") // Authored art colours and blend weights are the scene's tunable palette.

package nl.petrichor.app.ui.background

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import nl.petrichor.app.data.background.BackgroundCondition
import nl.petrichor.app.data.background.TimeBand
import nl.petrichor.app.data.background.WeatherMood

/**
 * One full set of scene colours for a single condition, consumed by ParkGeometry/ParkScene when
 * drawing a frame. Groups: sky gradient (skyTop/skyHorizon), celestial discs and stars (null
 * means "don't draw"), hills far→near, trees, ground and path, clouds, and the fog veil (non-null
 * only for FOG). Authored by [BackgroundPalettes] as 6 band bases plus per-mood modifiers, so
 * all 42 band x mood cells resolve to a non-null palette.
 */
data class BackgroundPalette(
    val skyTop: Color,
    val skyHorizon: Color,
    val sunDisc: Color?,
    val sunGlow: Color?,
    val moonDisc: Color?,
    val starColor: Color?,
    val hillFar: Color,
    val hillMid: Color,
    val hillNear: Color,
    val treeCanopy: Color,
    val treeCanopyDark: Color,
    val treeTrunk: Color,
    val grass: Color,
    val pathFill: Color,
    val pathEdge: Color,
    val cloudLight: Color,
    val cloudDark: Color,
    val fogColor: Color? = null,
)

/**
 * The 42 palettes (6 [TimeBand] bases x 7 [WeatherMood] treatments), composed once at startup.
 * Lookup happens in ParkScene only when the condition changes — never per animation frame.
 */
object BackgroundPalettes {
    private val bases =
        mapOf(
            TimeBand.DAWN to base(0xFF897BAA, 0xFFF7BEA7, 0xFF89978D, 0xFF647E77, 0xFF405D58, 0xFFC7B5AC),
            TimeBand.MORNING to base(0xFF6EAAD1, 0xFFD4E8E3, 0xFF99B9A4, 0xFF719A7D, 0xFF3D725B, 0xFFE0D3B5),
            TimeBand.AFTERNOON to base(0xFF458CC5, 0xFFCCE4DC, 0xFF8EAE96, 0xFF65926E, 0xFF34674E, 0xFFDCC8A2),
            TimeBand.DUSK to base(0xFF716E9F, 0xFFF2AA8D, 0xFF8D8896, 0xFF666C7B, 0xFF394F59, 0xFFBAA5A1),
            TimeBand.EVENING to base(0xFF263B68, 0xFFA07F91, 0xFF54637D, 0xFF394F65, 0xFF233B4C, 0xFF827E8F),
            TimeBand.NIGHT to base(0xFF111B3C, 0xFF344A68, 0xFF33445D, 0xFF23364C, 0xFF142938, 0xFF626B7D),
        )
    private val palettes =
        TimeBand.entries
            .flatMap { band ->
                WeatherMood.entries.map { mood ->
                    val condition = BackgroundCondition(band, mood)
                    condition to modify(bases.getValue(band), condition)
                }
            }.toMap()

    /** Palette for [condition]; every band x mood combination resolves (tested). */
    fun forCondition(condition: BackgroundCondition): BackgroundPalette = palettes.getValue(condition)

    /**
     * Builds a band's base palette from six authored colours (sky top/horizon, hills far/mid/near,
     * path); derived roles (trees, grass, cloud tones) are blends of these.
     */
    @Suppress("LongParameterList") // Six authored colours define a band without duplicating palette fields.
    private fun base(top: Long, horizon: Long, far: Long, mid: Long, near: Long, path: Long) =
        BackgroundPalette(
            skyTop = Color(top),
            skyHorizon = Color(horizon),
            sunDisc = Color(0xFFFFE4B3),
            sunGlow = Color(0xFFFFD09A),
            moonDisc = null,
            starColor = null,
            hillFar = Color(far),
            hillMid = Color(mid),
            hillNear = Color(near),
            treeCanopy = Color(near),
            treeCanopyDark = lerp(Color(near), Color(0xFF102F36), 0.3f),
            treeTrunk = lerp(Color(near), Color(0xFF242C35), 0.65f),
            grass = lerp(Color(mid), Color(near), 0.45f),
            pathFill = Color(path),
            pathEdge = lerp(Color(path), Color(near), 0.25f),
            cloudLight = lerp(Color(horizon), Color.White, 0.65f),
            cloudDark = Color(far),
        )

    /**
     * Applies the mood treatment to a band base: fair clouds for clear/partly cloudy, progressive
     * muting for overcast/rain, near-black for thunderstorm, whitening for snow, fog veil for fog.
     * Celestial discs are set per band first (sun by day, moon and stars at night).
     */
    private fun modify(
        base: BackgroundPalette,
        condition: BackgroundCondition,
    ): BackgroundPalette {
        val late = condition.band == TimeBand.NIGHT || condition.band == TimeBand.EVENING
        val celestial =
            base.copy(
                sunDisc = if (late) null else base.sunDisc,
                sunGlow = if (late) null else base.sunGlow,
                moonDisc = if (condition.band == TimeBand.NIGHT) Color(0xFFE8E6D2) else null,
                starColor = if (late) Color(0xFFD9E4F4) else null,
            )
        return when (condition.mood) {
            WeatherMood.CLEAR, WeatherMood.PARTLY_CLOUDY -> {
                fairClouds(celestial, late)
            }

            WeatherMood.OVERCAST -> {
                muted(celestial, 0.55f)
            }

            WeatherMood.RAIN -> {
                muted(celestial, 0.7f).let {
                    it.copy(pathFill = lerp(it.pathFill, it.skyHorizon, 0.35f))
                }
            }

            WeatherMood.THUNDERSTORM -> {
                muted(celestial, 0.9f).copy(
                    skyTop = Color(0xFF0C1220),
                    skyHorizon = Color(0xFF303746),
                    hillFar = lerp(base.hillFar, Color(0xFF293342), 0.75f),
                    hillMid = lerp(base.hillMid, Color(0xFF1E2C38), 0.75f),
                    hillNear = lerp(base.hillNear, Color(0xFF14222D), 0.75f),
                    treeCanopy = Color(0xFF192C32),
                    treeCanopyDark = Color(0xFF122329),
                    grass = lerp(base.grass, Color(0xFF1E2D36), 0.75f),
                    pathFill = lerp(base.pathFill, Color(0xFF394552), 0.65f),
                    cloudLight = Color(0xFF353C4B),
                    cloudDark = Color(0xFF202838),
                )
            }

            WeatherMood.SNOW -> {
                celestial.copy(
                    skyTop = lerp(base.skyTop, base.cloudLight, 0.6f),
                    hillFar = lerp(base.hillFar, base.cloudLight, 0.8f),
                    hillMid = lerp(base.hillMid, base.cloudLight, 0.85f),
                    hillNear = lerp(base.hillNear, base.cloudLight, 0.7f),
                    grass = lerp(base.grass, base.cloudLight, 0.85f),
                    pathEdge = base.cloudLight,
                    sunDisc = null,
                    sunGlow = null,
                )
            }

            WeatherMood.FOG -> {
                celestial.copy(
                    skyTop = lerp(base.skyTop, base.cloudLight, 0.4f),
                    hillFar = lerp(base.hillFar, base.skyHorizon, 0.8f),
                    hillMid = lerp(base.hillMid, base.skyHorizon, 0.55f),
                    fogColor = base.cloudLight,
                    starColor = null,
                )
            }
        }
    }

    /** Clear/partly-cloudy treatment: cloud tones follow the band (dark clouds at night). */
    private fun fairClouds(
        p: BackgroundPalette,
        late: Boolean,
    ): BackgroundPalette =
        p.copy(
            cloudLight = if (late) lerp(p.skyHorizon, p.skyTop, 0.3f) else p.cloudLight,
            cloudDark = if (late) p.skyTop else lerp(p.cloudLight, p.cloudDark, 0.2f),
        )

    /** Desaturates/darkens sky, hills and clouds towards grey by [amount]; also hides all celestial elements. */
    private fun muted(
        p: BackgroundPalette,
        amount: Float,
    ): BackgroundPalette =
        p.copy(
            skyTop = lerp(p.skyTop, Color(0xFF414F62), amount),
            skyHorizon = lerp(p.skyHorizon, p.cloudDark, amount),
            hillFar = lerp(p.hillFar, p.cloudDark, amount * 0.5f),
            hillMid = lerp(p.hillMid, p.treeTrunk, amount * 0.35f),
            hillNear = lerp(p.hillNear, p.treeTrunk, amount * 0.35f),
            cloudLight = lerp(p.cloudLight, p.cloudDark, amount),
            sunDisc = null,
            sunGlow = null,
            moonDisc = null,
            starColor = null,
        )
}
