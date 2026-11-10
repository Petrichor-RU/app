package nl.petrichor.app.data.wardrobe

import java.io.File
import io.github.ismoy.imagepickerkmp.extensions.loadBytes
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import io.github.ismoy.imagepickerkmp.extensions.loadPainter
import androidx.compose.ui.graphics.painter.Painter

internal actual suspend fun saveClothingImage(photo: PhotoResult, extension: String): String {
    val directory = File(System.getProperty("user.home"), ".petrichor/wardrobe-images")
        .apply { mkdirs() }
    val file = File(directory, "clothing_${System.currentTimeMillis()}.$extension")
    file.writeBytes(photo.loadBytes())
    return file.absolutePath
}

internal actual suspend fun deleteClothingImage(path: String) {
    check(File(path).delete() || !File(path).exists()) {
        "Could not delete clothing image."
    }
}

internal actual fun imageUriForLoading(path: String): String = File(path).toURI().toString()

internal actual fun loadClothingImagePainter(path: String): Painter? =
    PhotoResult(uri = imageUriForLoading(path)).loadPainter()

internal actual suspend fun loadClothingPhotoPainter(photo: PhotoResult): Painter? =
    photo.loadPainter()
