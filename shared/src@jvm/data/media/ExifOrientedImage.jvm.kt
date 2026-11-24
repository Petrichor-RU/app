package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface

/** Upper bound for stored/decoded image dimensions; larger photos are downsampled. */
internal const val MAX_IMAGE_DIMENSION = 2048

/**
 * Rotates the pixels of the image in [bytes] according to its EXIF orientation and
 * re-encodes it, so the result carries upright pixels and no EXIF orientation tag.
 * Falls back to the original [bytes] when the image cannot be decoded.
 */
internal fun normalizeExifOrientedImageBytes(
    bytes: ByteArray,
    preferPng: Boolean,
    jpegQuality: Int,
): ByteArray {
    val image = Image.makeFromEncoded(bytes)
    val normalized = drawUpright(image, exifTransform(exifOrientationOf(bytes)))
    val format = if (preferPng) EncodedImageFormat.PNG else EncodedImageFormat.JPEG
    return normalized.encodeToData(format, jpegQuality)?.bytes ?: bytes
}

/**
 * Decodes [bytes] into a painter with the EXIF orientation applied. Skia ignores the
 * EXIF orientation tag, so portrait photos would otherwise render rotated 90 degrees.
 */
internal fun decodeExifOrientedImagePainter(bytes: ByteArray): Painter {
    val image = Image.makeFromEncoded(bytes)
    val upright = drawUpright(image, exifTransform(exifOrientationOf(bytes)))
    return BitmapPainter(upright.toComposeImageBitmap())
}

internal fun drawUpright(
    image: Image,
    transform: ExifTransform?,
): Image {
    val swapDimensions = transform != null && (transform.degrees == 90f || transform.degrees == 270f)
    val uprightWidth = if (swapDimensions) image.height else image.width
    val uprightHeight = if (swapDimensions) image.width else image.height
    val scale = minOf(1f, MAX_IMAGE_DIMENSION.toFloat() / maxOf(uprightWidth, uprightHeight))
    val width = maxOf(1, (uprightWidth * scale).toInt())
    val height = maxOf(1, (uprightHeight * scale).toInt())
    val surface = Surface.makeRasterN32Premul(width, height)
    return when {
        transform == null && scale == 1f -> {
            image
        }

        else -> {
            surface.canvas.apply {
                translate(width / 2f, height / 2f)
                if (transform != null) {
                    scale(transform.scaleX, transform.scaleY)
                    rotate(transform.degrees)
                }
                scale(scale, scale)
                translate(-image.width / 2f, -image.height / 2f)
                drawImage(image, 0f, 0f)
            }
            surface.makeImageSnapshot()
        }
    }
}
