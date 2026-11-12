package nl.petrichor.app.data.media

import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Pixel-level regression tests for the EXIF orientation transform: a portrait camera
 * photo stored with orientation 6 must render upright (rotated 90 degrees clockwise),
 * not upside down or left as-is.
 */
class ExifOrientedImageTest {
    // 8x4 source: TL red, TR green, BL blue, BR white.
    private fun quadrantImage(): Image {
        val surface = Surface.makeRasterN32Premul(8, 4)
        val canvas = surface.canvas
        canvas.drawRect(Rect.makeLTRB(0f, 0f, 4f, 2f), Paint().apply { color = Color.RED })
        canvas.drawRect(Rect.makeLTRB(4f, 0f, 8f, 2f), Paint().apply { color = Color.GREEN })
        canvas.drawRect(Rect.makeLTRB(0f, 2f, 4f, 4f), Paint().apply { color = Color.BLUE })
        canvas.drawRect(Rect.makeLTRB(4f, 2f, 8f, 4f), Paint().apply { color = Color.WHITE })
        return surface.makeImageSnapshot()
    }

    private fun pixel(
        image: Image,
        x: Int,
        y: Int,
    ): Int = image.peekPixels()?.getColor(x, y) ?: error("no pixels")

    @Test
    fun `orientation 6 rotates the pixels 90 degrees clockwise`() {
        val result = drawUpright(quadrantImage(), exifTransform(6))
        // 90 CW swaps dimensions: 8x4 -> 4x8.
        assertEquals(4, result.width)
        assertEquals(8, result.height)
        // 90 CW: source TL -> result TR, source BL -> result TL, source BR -> result BL, source TR -> result BR.
        assertEquals(Color.RED, pixel(result, 3, 1)) // result TR = source TL (red)
        assertEquals(Color.BLUE, pixel(result, 1, 1)) // result TL = source BL (blue)
        assertEquals(Color.WHITE, pixel(result, 1, 6)) // result BL = source BR (white)
        assertEquals(Color.GREEN, pixel(result, 3, 6)) // result BR = source TR (green)
    }

    @Test
    fun `orientation 8 rotates the pixels 270 degrees clockwise`() {
        val result = drawUpright(quadrantImage(), exifTransform(8))
        assertEquals(4, result.width)
        assertEquals(8, result.height)
        // 270 CW (= 90 CCW): source TR -> result TL, source BR -> result TR, source TL -> result BL, source BL -> result BR.
        assertEquals(Color.GREEN, pixel(result, 1, 1)) // result TL = source TR (green)
        assertEquals(Color.WHITE, pixel(result, 3, 1)) // result TR = source BR (white)
        assertEquals(Color.RED, pixel(result, 1, 6)) // result BL = source TL (red)
        assertEquals(Color.BLUE, pixel(result, 3, 6)) // result BR = source BL (blue)
    }

    @Test
    fun `orientation 3 rotates 180 degrees`() {
        val result = drawUpright(quadrantImage(), exifTransform(3))
        assertEquals(8, result.width)
        assertEquals(4, result.height)
        assertEquals(Color.WHITE, pixel(result, 1, 1)) // TL = source BR
        assertEquals(Color.BLUE, pixel(result, 6, 1)) // TR = source BL
        assertEquals(Color.GREEN, pixel(result, 1, 3)) // BL = source TR
        assertEquals(Color.RED, pixel(result, 6, 3)) // BR = source TL
    }

    @Test
    fun `normal orientation returns the image unchanged`() {
        val source = quadrantImage()
        assertSame(source, drawUpright(source, exifTransform(1)))
        assertSame(source, drawUpright(source, null))
    }
}
