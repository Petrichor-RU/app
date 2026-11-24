@file:Suppress("TooManyFunctions") // platform storage actuals are small and belong together

package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.Painter
import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.js.JsAny
import kotlin.js.Promise

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("url => fetch(url).then(response => response.arrayBuffer())")
private external fun fetchImageBuffer(uri: String): Promise<JsAny?>

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("buffer => Array.from(new Uint8Array(buffer))")
private external fun imageBufferBytes(buffer: JsAny?): JsAny?

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("array => array.length")
private external fun jsArrayLength(array: JsAny?): Int

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(array, index) => array[index]")
private external fun jsArrayByte(
    array: JsAny?,
    index: Int,
): Int

@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun loadImageBytes(uri: String): ByteArray {
    val array = imageBufferBytes(fetchImageBuffer(uri).await())
    return ByteArray(jsArrayLength(array)) { index -> jsArrayByte(array, index).toByte() }
}

internal actual suspend fun saveMediaImage(
    photo: PhotoResult,
    extension: String,
    scope: MediaScope,
): String {
    // The scope only picks a directory on file-based platforms; a data URL is
    // self-contained, so it is not needed here.
    val bytes = loadImageBytes(photo.uri)
    check(bytes.isNotEmpty()) { "Selected image contains no data." }
    // Rotate the pixels according to the EXIF orientation and re-encode the image:
    // the re-encoded output carries no EXIF, so the orientation tag is stripped and
    // every loader sees an upright image.
    val preferPng = extension.equals("png", ignoreCase = true)
    val normalized = normalizeExifOrientedImageBytes(bytes, preferPng, STORED_IMAGE_JPEG_QUALITY)
    val mimeType = if (preferPng) "image/png" else "image/jpeg"
    return "data:$mimeType;base64,${Base64.encode(normalized)}"
}

internal actual suspend fun deleteStoredImage(path: String) = Unit

internal actual fun imageUriForLoading(path: String): String = path

internal actual fun loadImagePainter(path: String): Painter? =
    runCatching {
        val encoded = path.substringAfter(";base64,", missingDelimiterValue = "")
        if (encoded.isEmpty()) return@runCatching null
        // Stored data URLs are already normalized (upright, no EXIF) at save time.
        decodeImagePainter(Base64.decode(encoded))
    }.getOrNull()

internal actual suspend fun loadPickedPhotoPainter(photo: PhotoResult): Painter? {
    val bytes = loadImageBytes(photo.uri)
    if (bytes.isEmpty()) return null
    return decodeExifOrientedImagePainter(bytes)
}
