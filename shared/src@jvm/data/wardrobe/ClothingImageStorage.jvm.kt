package nl.petrichor.app.data.wardrobe

import java.io.File
import io.github.ismoy.imagepickerkmp.extensions.loadBytes
import io.github.ismoy.imagepickerkmp.picker.PhotoResult

internal actual fun saveClothingImage(photo: PhotoResult, extension: String): String {
    val directory = File(System.getProperty("user.home"), ".petrichor/wardrobe-images")
        .apply { mkdirs() }
    val file = File(directory, "clothing_${System.currentTimeMillis()}.$extension")
    file.writeBytes(photo.loadBytes())
    return file.absolutePath
}

internal actual fun deleteClothingImage(path: String) {
    check(File(path).delete() || !File(path).exists()) {
        "Could not delete clothing image."
    }
}
