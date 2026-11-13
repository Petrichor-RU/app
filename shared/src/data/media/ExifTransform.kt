package nl.petrichor.app.data.media

/**
 * Scale/rotation that renders an image stored with the given EXIF orientation upright.
 * Applied as rotate-then-scale, matching android.graphics.Matrix setScale/postRotate.
 * Orientation values follow the EXIF spec (1 = normal, 6 = rotate 90 CW, ...).
 */
internal data class ExifTransform(
    val scaleX: Float,
    val scaleY: Float,
    val degrees: Float,
)

internal fun exifTransform(orientation: Int): ExifTransform? =
    when (orientation) {
        2 -> ExifTransform(-1f, 1f, 0f)

        // mirror horizontal
        3 -> ExifTransform(1f, 1f, 180f)

        // rotate 180
        4 -> ExifTransform(1f, -1f, 0f)

        // mirror vertical
        5 -> ExifTransform(-1f, 1f, 90f)

        // transpose
        6 -> ExifTransform(1f, 1f, 90f)

        // rotate 90 CW
        7 -> ExifTransform(-1f, 1f, 270f)

        // transverse
        8 -> ExifTransform(1f, 1f, 270f)

        // rotate 270 CW
        else -> null
    }
