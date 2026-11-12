package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.Painter
import io.github.ismoy.imagepickerkmp.extensions.loadBytes
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import java.io.File
import java.net.URI

internal actual suspend fun saveMediaImage(
    photo: PhotoResult,
    extension: String,
    scope: MediaScope,
): String {
    val directory =
        File(System.getProperty("user.home"), ".petrichor/${scope.directoryName}")
            .apply { mkdirs() }
    val sourceBytes = readSourceBytes(photo.uri) ?: photo.loadBytes()
    check(sourceBytes.isNotEmpty()) { "Selected image contains no data." }
    // Rotate the pixels according to the EXIF orientation and re-encode the image:
    // the re-encoded output carries no EXIF, so the orientation tag is stripped and
    // every loader sees an upright image.
    val preferPng = extension.equals("png", ignoreCase = true)
    val normalized = normalizeExifOrientedImageBytes(sourceBytes, preferPng, STORED_IMAGE_JPEG_QUALITY)
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

internal actual fun imageUriForLoading(path: String): String = File(path).toURI().toString()

internal actual fun loadImagePainter(path: String): Painter? = loadExifOrientedPainter(imageUriForLoading(path))

internal actual suspend fun loadPickedPhotoPainter(photo: PhotoResult): Painter? = loadExifOrientedPainter(photo.uri)

private fun loadExifOrientedPainter(uri: String): Painter? = readSourceBytes(uri)?.let { decodeExifOrientedImagePainter(it) }

private fun readSourceBytes(uri: String): ByteArray? =
    try {
        File(URI(uri)).readBytes()
    } catch (_: Exception) {
        try {
            File(uri).readBytes()
        } catch (_: Exception) {
            null
        }
    }
