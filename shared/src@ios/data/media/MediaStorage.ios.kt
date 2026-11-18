package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.Painter
import io.github.ismoy.imagepickerkmp.extensions.absolutePath
import io.github.ismoy.imagepickerkmp.extensions.loadPainter
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun saveMediaImage(
    photo: PhotoResult,
    extension: String,
    scope: MediaScope,
): String {
    val fileManager = NSFileManager.defaultManager
    val directory = "${documentDirectory()}/${scope.directoryName}"
    if (!fileManager.fileExistsAtPath(directory)) {
        check(fileManager.createDirectoryAtPath(directory, true, null, null)) {
            "Could not create image directory."
        }
    }

    val filename = "image_${NSUUID.UUID().UUIDString}.$extension"
    val path = "$directory/$filename"
    val sourcePath = photo.absolutePath
    check(fileManager.fileExistsAtPath(sourcePath)) { "Selected image does not exist at $sourcePath." }
    SystemFileSystem.source(Path(sourcePath)).buffered().use { source ->
        SystemFileSystem.sink(Path(path)).use { sink ->
            source.transferTo(sink)
        }
    }
    check(fileManager.fileExistsAtPath(path)) { "Copied image does not exist at $path." }
    return "${scope.directoryName}/$filename"
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun deleteStoredImage(path: String) {
    val fileManager = NSFileManager.defaultManager
    val filePath = imageUriForLoading(path)
    check(fileManager.removeItemAtPath(filePath, null) || !fileManager.fileExistsAtPath(filePath)) {
        "Could not delete stored image."
    }
}

internal actual fun imageUriForLoading(path: String): String =
    requireNotNull(NSURL.fileURLWithPath("${documentDirectory()}/$path").absoluteString)

internal actual fun loadImagePainter(path: String): Painter? = PhotoResult(uri = imageUriForLoading(path)).loadPainter()

internal actual suspend fun loadPickedPhotoPainter(photo: PhotoResult): Painter? = photo.loadPainter()

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val url =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    return checkNotNull(url?.path) { "Could not resolve the iOS documents directory." }
}
