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
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import io.github.ismoy.imagepickerkmp.extensions.loadBytes
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import java.io.File
import java.io.InputStream

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
    // Store the picked bytes verbatim, keeping the EXIF orientation tag. The tag is
    // applied exactly once, at load time (see loadExifOrientedPainter); re-encoding
    // here would risk dropping the tag without rotating (or rotating twice), which
    // is what made freshly taken camera photos render upside down.
    val storedExtension =
        extension.lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp", "heic") } ?: "jpg"
    val file = File(directory, "image_${System.currentTimeMillis()}.$storedExtension")
    file.writeBytes(sourceBytes)
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

private fun loadExifOrientedPainter(uri: String): Painter? =
    painterCache.get(uri)
        ?: readImageBytes(uri)?.let { bytes ->
            decodeExifOrientedBitmap(bytes, exifOrientationOfUri(uri, bytes))?.let { bitmap ->
                BitmapPainter(bitmap.asImageBitmap()).also { painterCache.put(uri, it) }
            }
        }

/**
 * Reads the EXIF orientation for [uri]. When [uri] is a local file we read the tag
 * from the file path (ExifInterface(String)), the same constructor the image
 * picker uses — the InputStream-based constructor misreads the tag on some
 * camera JPEGs, which made freshly taken photos render upside down. Falls back
 * to parsing [bytes] for content:// URIs.
 */
private fun exifOrientationOfUri(
    uri: String,
    bytes: ByteArray,
): Int {
    val file =
        when {
            uri.startsWith("file://") -> File(uri.toUri().path.orEmpty())
            uri.startsWith("content://") -> null
            else -> File(uri)
        }
    if (file != null && file.exists()) {
        return runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }
    return exifOrientationOf(bytes)
}

/**
 * Decodes [bytes] into a bitmap with the EXIF [orientation] applied. BitmapFactory
 * ignores the EXIF orientation tag, so portrait photos would otherwise render
 * rotated 90 degrees.
 */
private fun decodeExifOrientedBitmap(
    bytes: ByteArray,
    orientation: Int,
): Bitmap? {
    val decoded = decodeDownsampled(bytes) ?: return null
    return applyExifTransform(decoded, orientation)
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
        // Read our own files directly: going through the content resolver for
        // file:// URIs can fail on some devices, which would drop us onto the
        // picker's re-encoding loadBytes() path that strips the EXIF tag.
        uri.startsWith("file://") -> {
            File(uri.toUri().path.orEmpty()).inputStream()
        }

        uri.startsWith("content://") -> {
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
