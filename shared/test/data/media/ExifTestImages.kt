package nl.petrichor.app.data.media

/**
 * Builds a minimal JPEG carrying an EXIF APP1 segment with the given orientation tag
 * (0x0112), mimicking what cameras and encoders like PIL produce.
 */
internal fun jpegWithOrientation(
    orientation: Int,
    littleEndian: Boolean,
): ByteArray {
    val tiff = mutableListOf<Int>()

    fun u16(value: Int) {
        if (littleEndian) {
            tiff += value and 0xFF
            tiff += (value shr 8) and 0xFF
        } else {
            tiff += (value shr 8) and 0xFF
            tiff += value and 0xFF
        }
    }

    fun u32(value: Int) {
        if (littleEndian) {
            tiff += value and 0xFF
            tiff += (value shr 8) and 0xFF
            tiff += (value shr 16) and 0xFF
            tiff += (value shr 24) and 0xFF
        } else {
            tiff += (value shr 24) and 0xFF
            tiff += (value shr 16) and 0xFF
            tiff += (value shr 8) and 0xFF
            tiff += value and 0xFF
        }
    }
    // TIFF header: byte order, magic 42, offset of IFD0 (8).
    tiff += if (littleEndian) 'I'.code else 'M'.code
    tiff += if (littleEndian) 'I'.code else 'M'.code
    u16(42)
    u32(8)
    // IFD0: one entry (orientation tag 0x0112, type SHORT, count 1, value), next IFD = 0.
    u16(1)
    u16(0x0112)
    u16(3)
    u32(1)
    u16(orientation)
    u16(0)
    u32(0)
    // JPEG: SOI, APP1 with "Exif\0\0" + TIFF, EOI. Only the E in "Exif" is uppercase.
    val app1Length = 2 + 6 + tiff.size
    val out = mutableListOf<Int>()
    out += 0xFF
    out += 0xD8
    out += 0xFF
    out += 0xE1
    out += (app1Length shr 8) and 0xFF
    out += app1Length and 0xFF
    out += 'E'.code
    out += 'x'.code
    out += 'i'.code
    out += 'f'.code
    out += 0
    out += 0
    out.addAll(tiff)
    out += 0xFF
    out += 0xD9
    return ByteArray(out.size) { index -> out[index].toByte() }
}

/** Prepends a JFIF APP0 segment after the SOI marker, as real encoders do. */
internal fun ByteArray.withJfifApp0(): ByteArray {
    // Payload must be exactly 14 bytes so the declared length (0x10) is consistent.
    val jfifPayload = "JFIF\u0000\u0001\u0001\u0000\u0000\u0001\u0000\u0001\u0000\u0000".map { it.code }
    val jfif = listOf(0xFF, 0xE0, 0x00, 0x10) + jfifPayload
    val out =
        ByteArray(size + jfif.size) { index ->
            if (index < 2) {
                this[index]
            } else if (index < 2 + jfif.size) {
                jfif[index - 2].toByte()
            } else {
                this[index - jfif.size]
            }
        }
    return out
}

/** Inserts 0xFF fill bytes before the first APP1 marker, as some cameras produce. */
internal fun ByteArray.withFillBytesBeforeApp1(): ByteArray {
    val marker =
        withIndex()
            .first {
                it.index >= 2 && it.value == 0xFF.toByte() && getOrNull(it.index + 1) == 0xE1.toByte()
            }.index
    return toMutableList().apply { addAll(marker, listOf(0xFF.toByte(), 0xFF.toByte())) }.toByteArray()
}
