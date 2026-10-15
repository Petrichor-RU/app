package nl.petrichor.app.data.wardrobe

import kotlinx.cinterop.ExperimentalForeignApi
import io.github.ismoy.imagepickerkmp.extensions.absolutePath
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL

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
    println("[ClothingImageStorage:iOS] Source URI: ${photo.uri}")
    println("[ClothingImageStorage:iOS] Destination path: $path")
    val sourcePath = photo.absolutePath
    val sourceFileExists = fileManager.fileExistsAtPath(sourcePath)
    val sourceSize = fileManager.attributesOfItemAtPath(sourcePath, null)?.get("NSFileSize")
    println(
        "[ClothingImageStorage:iOS] Resolved source path: $sourcePath, " +
            "exists=$sourceFileExists, size=$sourceSize"
    )
    check(sourceFileExists) { "Selected clothing image does not exist at $sourcePath." }
    SystemFileSystem.source(Path(sourcePath)).buffered().use { source ->
        SystemFileSystem.sink(Path(path)).use { sink ->
            source.transferTo(sink)
        }
    }
    val destinationExists = fileManager.fileExistsAtPath(path)
    val destinationSize = fileManager.attributesOfItemAtPath(path, null)?.get("NSFileSize")
    println(
        "[ClothingImageStorage:iOS] Copy complete: exists=$destinationExists, " +
            "size=$destinationSize"
    )
    check(destinationExists) { "Copied clothing image does not exist at $path." }
    check(destinationSize is Number && destinationSize.toLong() > 0) {
        "Copied clothing image is empty at $path."
    }
    return "wardrobe-images/$filename"
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun deleteClothingImage(path: String) {
    val fileManager = NSFileManager.defaultManager
    val filePath = imageUriForLoading(path)
    check(fileManager.removeItemAtPath(filePath, null) || !fileManager.fileExistsAtPath(filePath)) {
        "Could not delete clothing image."
    }
}

internal actual fun imageUriForLoading(path: String): String {
    return requireNotNull(NSURL.fileURLWithPath("${documentDirectory()}/$path").absoluteString)
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
