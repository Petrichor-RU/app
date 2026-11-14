@file:Suppress("MagicNumber") // Cached normalised vector artwork; all coordinates are deliberate design values.

package nl.petrichor.app.ui.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import nl.petrichor.app.data.background.TimeBand
import kotlin.math.min
import kotlin.random.Random

// The path disappears at the same ridge point used by the middle hill's two curves.
private const val HILL_JOIN_X = 0.55f
private const val FAR_HILL_HEIGHT = 0.54f
private const val MIDDLE_HILL_HEIGHT = 0.65f

/**
 * Cached vector geometry for one frame of the park scene: hills, grass, the winding path,
 * edge-anchored trees (plus a second, smaller cluster per side on >=768 dp viewports so wide
 * screens don't look empty), the cloud shape, celestial positions and weather particle seeds.
 * Everything is drawn in a normalised space mapped to [viewport], so the scene works at any
 * aspect ratio. Built once per palette/canvas-size change inside ParkScene's drawWithCache —
 * animation frames only read these paths and brushes, they never allocate.
 */
internal class ParkGeometry(
    private val viewport: Size,
    density: Float,
    private val palette: BackgroundPalette,
) {
    /** min(width, height): the base unit all drawing scales from. */
    val unit = min(viewport.width, viewport.height)

    /** Fixed star field in the top 42% of the sky, drawn only when the palette has a star colour. */
    val stars = sceneParticles(32).map { Offset(it.x * viewport.width, it.y * viewport.height * 0.42f) }

    /** Shared seed positions for rain and snow, in 0..1 normalised space. */
    val particles = sceneParticles(SNOW_PARTICLES)

    // Seeded, irregular timing with >=10 seconds between 200ms flashes in adjacent cycles.
    val flashOffsets = Random(91).let { random -> FloatArray(64) { 2.1f + random.nextFloat() * 1.8f } }
    private val hills = listOf(hill(FAR_HILL_HEIGHT, 0.05f), hill(MIDDLE_HILL_HEIGHT, -0.07f), hill(0.76f, 0.06f))
    private val grass = hill(0.9f, -0.025f)
    private val pathWidth = (viewport.width * 0.35f).coerceIn(110f * density, 400f * density)
    private val pathHorizon = Offset(viewport.width * HILL_JOIN_X, viewport.height * MIDDLE_HILL_HEIGHT)
    private val pathEdge = parkPath(pathWidth + unit * 0.009f)
    private val path = parkPath(pathWidth)

    // Blend the distant trail into the hills, keeping warmer earth tones in the foreground.
    // Brushes are cached with geometry and palette, rather than rebuilt during animation frames.
    private val pathFillBrush =
        Brush.verticalGradient(
            0f to palette.hillMid,
            0.3f to lerp(palette.hillNear, palette.pathFill, 0.45f),
            1f to lerp(palette.pathFill, palette.grass, 0.15f),
            startY = pathHorizon.y,
            endY = viewport.height,
        )
    private val pathEdgeBrush =
        Brush.verticalGradient(
            0f to palette.hillMid,
            0.3f to lerp(palette.hillNear, palette.pathEdge, 0.25f),
            1f to lerp(palette.pathEdge, palette.grass, 0.5f),
            startY = pathHorizon.y,
            endY = viewport.height,
        )
    val cloud =
        Path().apply {
            val u = unit
            moveTo(0f, 0f)
            cubicTo(-u * 0.04f, -u * 0.06f, u * 0.03f, -u * 0.1f, u * 0.08f, -u * 0.08f)
            cubicTo(u * 0.1f, -u * 0.2f, u * 0.25f, -u * 0.19f, u * 0.28f, -u * 0.08f)
            cubicTo(u * 0.39f, -u * 0.1f, u * 0.45f, 0f, u * 0.35f, u * 0.015f)
            close()
        }
    private val trees =
        buildList {
            add(tree(-unit * 0.04f, viewport.height * 0.82f, unit * 0.85f))
            add(tree(viewport.width - unit * 0.08f, viewport.height * 0.86f, unit * 0.75f))
            if (viewport.width / density >= 768f) {
                add(tree(unit * 0.17f, viewport.height * 0.72f, unit * 0.43f))
                add(tree(viewport.width - unit * 0.23f, viewport.height * 0.73f, unit * 0.47f))
            }
        }.sortedBy { it.base }

    /** Celestial disc position for [band]: low in the east at dawn, high at afternoon, low in the west at dusk. */
    fun sunPosition(band: TimeBand): Offset =
        when (band) {
            TimeBand.DAWN -> Offset(viewport.width * 0.23f, viewport.height * 0.46f)
            TimeBand.MORNING -> Offset(viewport.width * 0.32f, viewport.height * 0.26f)
            TimeBand.AFTERNOON -> Offset(viewport.width * 0.63f, viewport.height * 0.19f)
            TimeBand.DUSK -> Offset(viewport.width * 0.78f, viewport.height * 0.46f)
            else -> Offset(viewport.width * 0.72f, viewport.height * 0.22f)
        }

    /** Draws hills, grass, path and trees back to front in the palette's colours. */
    fun drawLandscape(scope: DrawScope) =
        with(scope) {
            val p = palette
            drawPath(hills[0], p.hillFar)
            drawPath(hills[1], p.hillMid)
            drawPath(hills[2], p.hillNear)
            drawPath(grass, p.grass)
            drawPath(pathEdge, pathEdgeBrush)
            drawPath(path, pathFillBrush)
            for (tree in trees) {
                drawPath(tree.trunk, p.treeTrunk)
                drawPath(tree.canopy, p.treeCanopy)
                drawPath(tree.shade, p.treeCanopyDark)
            }
        }

    private fun hill(
        height: Float,
        bend: Float,
    ) = Path().apply {
        moveTo(0f, viewport.height * height)
        cubicTo(
            viewport.width * 0.25f,
            viewport.height * (height - bend),
            viewport.width * 0.3f,
            viewport.height * (height + bend),
            viewport.width * HILL_JOIN_X,
            viewport.height * height,
        )
        cubicTo(
            viewport.width * 0.8f,
            viewport.height * (height - bend),
            viewport.width * 0.9f,
            viewport.height * (height + bend),
            viewport.width,
            viewport.height * height,
        )
        lineTo(viewport.width, viewport.height)
        lineTo(0f, viewport.height)
        close()
    }

    private fun parkPath(width: Float) =
        Path().apply {
            val centre = viewport.width / 2f
            val distantBendY = pathHorizon.y + (viewport.height - pathHorizon.y) * 0.28f
            moveTo(pathHorizon.x, pathHorizon.y)
            cubicTo(
                pathHorizon.x + unit * 0.24f,
                distantBendY,
                centre - unit * 0.25f,
                viewport.height * 0.8f,
                centre - width / 2f,
                viewport.height,
            )
            lineTo(centre + width / 2f, viewport.height)
            cubicTo(
                centre - unit * 0.05f,
                viewport.height * 0.81f,
                pathHorizon.x + unit * 0.27f,
                distantBendY,
                pathHorizon.x,
                pathHorizon.y,
            )
            close()
        }
}

/** One flat-canopy tree: trunk, canopy and a darker shade facet, sorted by base for draw order. */
private data class ParkTree(
    val base: Float,
    val trunk: Path,
    val canopy: Path,
    val shade: Path,
)

/** Builds a tree of [height] with its base at ([x], [y]); all shapes are unit-scaled paths. */
private fun tree(
    x: Float,
    y: Float,
    height: Float,
): ParkTree {
    val trunk =
        Path().apply {
            moveTo(x - height * 0.025f, y)
            lineTo(x - height * 0.012f, y - height * 0.7f)
            lineTo(x + height * 0.025f, y - height * 0.7f)
            lineTo(x + height * 0.045f, y)
            close()
        }
    val canopy =
        Path().apply {
            moveTo(x - height * 0.23f, y - height * 0.36f)
            cubicTo(x - height * 0.43f, y - height * 0.53f, x - height * 0.18f, y - height, x, y - height)
            cubicTo(x + height * 0.25f, y - height * 1.03f, x + height * 0.38f, y - height * 0.52f, x + height * 0.22f, y - height * 0.36f)
            cubicTo(x + height * 0.1f, y - height * 0.28f, x - height * 0.1f, y - height * 0.28f, x - height * 0.23f, y - height * 0.36f)
            close()
        }
    val shade =
        Path().apply {
            moveTo(x - height * 0.23f, y - height * 0.36f)
            cubicTo(x - height * 0.05f, y - height * 0.49f, x + height * 0.15f, y - height * 0.4f, x + height * 0.24f, y - height * 0.62f)
            cubicTo(x + height * 0.4f, y - height * 0.3f, x - height * 0.1f, y - height * 0.27f, x - height * 0.23f, y - height * 0.36f)
            close()
        }
    return ParkTree(y, trunk, canopy, shade)
}
