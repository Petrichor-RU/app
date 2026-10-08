package nl.petrichor.app.data.wardrobe

import kotlinx.cinterop.ExperimentalForeignApi
import io.github.ismoy.imagepickerkmp.extensions.asSource
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun saveClothingImage(photo: PhotoResult, extension: String): String {
    val fileManager = NSFileManager.defaultManager
    val directory = "${documentDirectory()}/wardrobe-images"
    if (!fileManager.fileExistsAtPath(directory)) {
        check(fileManager.createDirectoryAtPath(directory, true, null, null)) {
            "Could not create clothing image directory."
        }
    }

    val filename = "clothing_${NSUUID.UUID().UUIDString}.$extension"
    val path = "$directory/$filename"
    photo.asSource().use { source ->
        SystemFileSystem.sink(Path(path)).use { sink ->
            source.transferTo(sink)
        }
    }
    return path
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null
    )
    return checkNotNull(url?.path) { "Could not resolve the iOS documents directory." }
}
