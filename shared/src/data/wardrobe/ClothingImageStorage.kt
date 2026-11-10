package nl.petrichor.app.data.wardrobe

import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ClothingImageStorage {
    suspend fun copyToPermanentStorage(photo: PhotoResult): String = withContext(Dispatchers.Default) {
        val extension = photo.fileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) }
            ?: photo.mimeType
                ?.substringAfter('/', missingDelimiterValue = "")
                ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) }
            ?: "jpg"
        saveClothingImage(photo, extension)
    }

    suspend fun deleteImage(path: String) = withContext(Dispatchers.Default) {
        if (path.isNotEmpty()) {
            deleteClothingImage(path)
        }
    }

    suspend fun loadPhotoPainter(photo: PhotoResult): Painter? =
        loadClothingPhotoPainter(photo)
}

internal expect suspend fun saveClothingImage(photo: PhotoResult, extension: String): String
internal expect suspend fun deleteClothingImage(path: String)
internal expect fun imageUriForLoading(path: String): String
internal expect fun loadClothingImagePainter(path: String): Painter?
internal expect suspend fun loadClothingPhotoPainter(photo: PhotoResult): Painter?
