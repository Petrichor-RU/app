package nl.petrichor.app.data.media

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

/**
 * Reads the EXIF orientation (tag 0x0112) from image [bytes] using the AndroidX
 * ExifInterface library. Returns 1 (normal) when the image carries no orientation
 * tag or cannot be parsed. BitmapFactory ignores this tag, which is why portrait
 * photos otherwise render rotated 90 degrees.
 */
@Suppress("TooGenericExceptionCaught") // any parse failure means "no orientation tag"
internal fun exifOrientationOf(bytes: ByteArray): Int =
    try {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
