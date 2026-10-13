package nl.petrichor.app.data.wardrobe

import io.github.ismoy.imagepickerkmp.picker.PhotoResult
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
}

internal expect fun saveClothingImage(photo: PhotoResult, extension: String): String
