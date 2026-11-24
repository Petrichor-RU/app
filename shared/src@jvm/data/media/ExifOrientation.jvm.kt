package nl.petrichor.app.data.media

import com.ashampoo.kim.Kim
import com.ashampoo.kim.format.tiff.constant.TiffTag

/**
 * Reads the EXIF orientation (tag 0x0112) from image [bytes] using the kim metadata
 * library. Returns 1 (normal) when the image carries no orientation tag or cannot be
 * parsed. Skia ignores this tag, which is why portrait photos otherwise render
 * rotated 90 degrees.
 */
@Suppress("TooGenericExceptionCaught") // any parse failure means "no orientation tag"
internal fun exifOrientationOf(bytes: ByteArray): Int =
    try {
        Kim.readMetadata(bytes)?.findShortValue(TiffTag.TIFF_TAG_ORIENTATION)?.toInt()
            ?: TiffTag.ORIENTATION_VALUE_HORIZONTAL_NORMAL
    } catch (_: Exception) {
        TiffTag.ORIENTATION_VALUE_HORIZONTAL_NORMAL
    }
