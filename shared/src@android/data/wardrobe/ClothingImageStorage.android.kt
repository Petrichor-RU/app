package nl.petrichor.app.data.wardrobe

import android.content.Context
import io.github.ismoy.imagepickerkmp.extensions.loadBytes
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import java.io.File

private var appContext: Context? = null

fun initClothingImageStorage(context: Context) {
    appContext = context.applicationContext
}

internal actual fun saveClothingImage(photo: PhotoResult, extension: String): String {
    val context = checkNotNull(appContext) {
        "ClothingImageStorage must be initialized before saving images."
    }
    val directory = File(context.filesDir, "wardrobe-images").apply { mkdirs() }
    val file = File(directory, "clothing_${System.currentTimeMillis()}.$extension")
    file.writeBytes(photo.loadBytes())
    return file.absolutePath
}
