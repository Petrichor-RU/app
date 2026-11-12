package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.Painter
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** JPEG quality used when stored images are re-encoded during EXIF normalization. */
internal const val STORED_IMAGE_JPEG_QUALITY = 90

/**
 * Storage location for a saved image. Each scope maps to its own directory so images
 * stay organized per feature; paths already stored in the database keep working
 * because the directory name is part of the stored path.
 */
enum class MediaScope(
    val directoryName: String,
) {
    WARDROBE("wardrobe-images"),
    OUTFIT("outfit-images"),
}

object MediaStorage {
    suspend fun copyToPermanentStorage(
        photo: PhotoResult,
        scope: MediaScope = MediaScope.WARDROBE,
    ): String =
        withContext(Dispatchers.Default) {
            val extension =
                photo.fileName
                    ?.substringAfterLast('.', missingDelimiterValue = "")
                    ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) }
                    ?: photo.mimeType
                        ?.substringAfter('/', missingDelimiterValue = "")
                        ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) }
                    ?: "jpg"
            saveMediaImage(photo, extension, scope)
        }

    suspend fun deleteImage(path: String) =
        withContext(Dispatchers.Default) {
            if (path.isNotEmpty()) {
                deleteStoredImage(path)
            }
        }

    suspend fun loadPhotoPainter(photo: PhotoResult): Painter? = loadPickedPhotoPainter(photo)
}

internal expect suspend fun saveMediaImage(
    photo: PhotoResult,
    extension: String,
    scope: MediaScope,
): String

internal expect suspend fun deleteStoredImage(path: String)

internal expect fun imageUriForLoading(path: String): String

internal expect fun loadImagePainter(path: String): Painter?

internal expect suspend fun loadPickedPhotoPainter(photo: PhotoResult): Painter?
