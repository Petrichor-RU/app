package nl.petrichor.app.data.wardrobe

import io.github.ismoy.imagepickerkmp.picker.PhotoResult
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlinx.coroutines.await

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
private external fun jsArrayByte(array: JsAny?, index: Int): Int

@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun loadImageBytes(uri: String): ByteArray {
    val array = imageBufferBytes(fetchImageBuffer(uri).await())
    return ByteArray(jsArrayLength(array)) { index -> jsArrayByte(array, index).toByte() }
}

@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun loadPhotoPainter(photo: PhotoResult): Painter? =
    runCatching {
        val bytes = loadImageBytes(photo.uri)
        if (bytes.isEmpty()) return@runCatching null
        BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
    }.getOrNull()

internal actual suspend fun saveClothingImage(photo: PhotoResult, extension: String): String {
    val mimeType = photo.mimeType
        ?.takeIf { it.startsWith("image/") }
        ?: "image/$extension"
    val bytes = loadImageBytes(photo.uri)
    check(bytes.isNotEmpty()) { "Selected clothing image contains no data." }
    return "data:$mimeType;base64,${Base64.encode(bytes)}"
}

internal actual suspend fun deleteClothingImage(path: String) = Unit

internal actual fun imageUriForLoading(path: String): String = path

internal actual fun loadClothingImagePainter(path: String): Painter? =
    runCatching {
        val encoded = path.substringAfter(";base64,", missingDelimiterValue = "")
        if (encoded.isEmpty()) return@runCatching null
        BitmapPainter(Image.makeFromEncoded(Base64.decode(encoded)).toComposeImageBitmap())
    }.getOrNull()

internal actual suspend fun loadClothingPhotoPainter(photo: PhotoResult): Painter? =
    loadPhotoPainter(photo)
