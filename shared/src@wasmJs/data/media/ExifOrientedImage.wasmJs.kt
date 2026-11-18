package nl.petrichor.app.data.media

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.await
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * Decodes an image through the browser's <img> element and re-exports it via canvas.
 * Browsers apply the EXIF orientation when decoding (image-orientation: from-image is
 * the default), and the canvas export carries no EXIF metadata — so the result is
 * upright pixels with the orientation tag stripped, without manual metadata parsing.
 */
@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (dataUrl, mime, quality) => {
        const img = new Image();
        img.src = dataUrl;
        return new Promise((resolve, reject) => {
            img.onload = () => {
                const canvas = document.createElement('canvas');
                canvas.width = img.naturalWidth;
                canvas.height = img.naturalHeight;
                canvas.getContext('2d').drawImage(img, 0, 0);
                resolve(canvas.toDataURL(mime, quality));
            };
            img.onerror = () => reject(new Error('Could not decode image'));
        });
    }
    """,
)
private external fun decodeUprightToDataUrl(
    dataUrl: String,
    mime: String,
    quality: Double,
): Promise<JsAny?>

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(x) => String(x)")
private external fun jsAnyToString(x: JsAny?): String

/**
 * Rotates the pixels of the image in [bytes] according to its EXIF orientation and
 * re-encodes it, so the result carries upright pixels and no EXIF orientation tag.
 * Falls back to the original [bytes] when the browser cannot decode the image.
 */
internal suspend fun normalizeExifOrientedImageBytes(
    bytes: ByteArray,
    preferPng: Boolean,
    jpegQuality: Int,
): ByteArray {
    val dataUrl = decodeUprightToDataUrl(bytes, if (preferPng) "image/png" else "image/jpeg", jpegQuality / 100.0) ?: return bytes
    return Base64.decode(dataUrl.substringAfter(";base64,", missingDelimiterValue = ""))
}

/** Decodes [bytes] into a painter with the EXIF orientation applied by the browser. */
internal suspend fun decodeExifOrientedImagePainter(bytes: ByteArray): Painter? {
    val dataUrl = decodeUprightToDataUrl(bytes, "image/jpeg", STORED_IMAGE_JPEG_QUALITY / 100.0) ?: return null
    return decodeImagePainter(Base64.decode(dataUrl.substringAfter(";base64,", missingDelimiterValue = "")))
}

/** Plain decode for already-normalized images (e.g. stored data URLs). */
internal fun decodeImagePainter(bytes: ByteArray): Painter = BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())

@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun decodeUprightToDataUrl(
    bytes: ByteArray,
    targetMime: String,
    quality: Double,
): String? {
    val dataUrl = "data:${sniffImageMime(bytes)};base64,${Base64.encode(bytes)}"
    val result = decodeUprightToDataUrl(dataUrl, targetMime, quality).await()
    return result?.let { jsAnyToString(it) }
}

/** Sniffs the container format from magic bytes so the data URL gets a usable image mime. */
private fun sniffImageMime(bytes: ByteArray): String {
    fun matches(vararg magic: Int): Boolean = magic.withIndex().all { bytes.getOrNull(it.index)?.toInt()?.and(0xFF) == it.value }

    return when {
        matches(0xFF, 0xD8) -> "image/jpeg"
        matches(0x89, 'P'.code, 'N'.code, 'G'.code) -> "image/png"
        matches('R'.code, 'I'.code, 'F'.code, 'F'.code) && bytes.getOrNull(8)?.toInt()?.and(0xFF) == 'W'.code -> "image/webp"
        matches('G'.code, 'I'.code, 'F'.code) -> "image/gif"
        else -> "image/jpeg"
    }
}
