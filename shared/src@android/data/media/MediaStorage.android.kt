@file:Suppress("TooManyFunctions") // platform storage actuals are small and belong together

package nl.petrichor.app.data.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import io.github.ismoy.imagepickerkmp.extensions.loadBytes
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import androidx.core.net.toUri

/** Decoded painters are cached so scrolling does not re-decode every image. */
private val painterCache = LruCache<String, Painter>(50)

/** Upper bound for the decoded image size; larger photos are downsampled. */
private const val MAX_IMAGE_DIMENSION = 2048

/**
 * Returns the current application context without holding one in a field: the process
 * application is resolved on demand (same technique the bundled image picker uses),
 * so there is no stored context that could go stale and no init call to forget.
 */
@SuppressLint("PrivateApi")
@Suppress("TooGenericExceptionCaught") // reflection failures all mean "try the next lookup"
private fun applicationContext(): Context =
    try {
        Class
            .forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as Context
    } catch (_: Exception) {
        try {
            Class
                .forName("android.app.AppGlobals")
                .getMethod("getInitialApplication")
                .invoke(null) as Context
        } catch (second: Exception) {
            throw IllegalStateException("Cannot obtain the application context.", second)
        }
    }

internal actual suspend fun saveMediaImage(
    photo: PhotoResult,
    extension: String,
    scope: MediaScope,
): String {
    val directory = File(applicationContext().filesDir, scope.directoryName).apply { mkdirs() }
    val sourceBytes = readImageBytes(photo.uri) ?: photo.loadBytes()
    check(sourceBytes.isNotEmpty()) { "Selected image contains no data." }
    // Rotate the pixels according to the EXIF orientation and re-encode the image:
    // the re-encoded output carries no EXIF, so the orientation tag is stripped and
    // every loader sees an upright image.
    val preferPng = extension.equals("png", ignoreCase = true)
    val normalized = normalizeImageBytes(sourceBytes, preferPng)
    val storedExtension = if (preferPng) "png" else "jpg"
    val file = File(directory, "image_${System.currentTimeMillis()}.$storedExtension")
    file.writeBytes(normalized)
    return file.absolutePath
}

internal actual suspend fun deleteStoredImage(path: String) {
    check(File(path).delete() || !File(path).exists()) {
        "Could not delete stored image."
    }
}

internal actual fun imageUriForLoading(path: String): String = path

internal actual fun loadImagePainter(path: String): Painter? = loadExifOrientedPainter(imageUriForLoading(path))

internal actual suspend fun loadPickedPhotoPainter(photo: PhotoResult): Painter? = loadExifOrientedPainter(photo.uri)

private fun normalizeImageBytes(
    bytes: ByteArray,
    preferPng: Boolean,
): ByteArray {
    val decoded = decodeDownsampled(bytes) ?: return bytes
    val rotated = applyExifTransform(decoded, exifOrientationOf(bytes))
    if (rotated !== decoded) decoded.recycle()
    return ByteArrayOutputStream().use { out ->
        val format = if (preferPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        rotated.compress(format, STORED_IMAGE_JPEG_QUALITY, out)
        out.toByteArray()
    }
}

private fun loadExifOrientedPainter(uri: String): Painter? =
    painterCache.get(uri)
        ?: readImageBytes(uri)?.let { bytes ->
            decodeExifOrientedBitmap(bytes)?.let { bitmap ->
                BitmapPainter(bitmap.asImageBitmap()).also { painterCache.put(uri, it) }
            }
        }

/**
 * Decodes [bytes] into a bitmap with the EXIF orientation applied. BitmapFactory
 * ignores the EXIF orientation tag, so portrait photos would otherwise render
 * rotated 90 degrees.
 */
private fun decodeExifOrientedBitmap(bytes: ByteArray): Bitmap? {
    val decoded = decodeDownsampled(bytes) ?: return null
    return applyExifTransform(decoded, exifOrientationOf(bytes))
}

private fun decodeDownsampled(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options =
        BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight))
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun applyExifTransform(
    bitmap: Bitmap,
    orientation: Int,
): Bitmap {
    val transform = exifTransform(orientation) ?: return bitmap
    val matrix =
        Matrix().apply {
            setScale(transform.scaleX, transform.scaleY)
            postRotate(transform.degrees)
        }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun readImageBytes(uri: String): ByteArray? =
    try {
        openImageStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

private fun openImageStream(uri: String): InputStream? =
    when {
        uri.startsWith("content://") || uri.startsWith("file://") -> {
            applicationContext().contentResolver.openInputStream(uri.toUri())
        }

        else -> {
            File(uri).inputStream()
        }
    }

private fun sampleSizeFor(maxDimension: Int): Int {
    var sampleSize = 1
    while (maxDimension / (sampleSize * 2) >= MAX_IMAGE_DIMENSION) {
        sampleSize *= 2
    }
    return sampleSize
}
