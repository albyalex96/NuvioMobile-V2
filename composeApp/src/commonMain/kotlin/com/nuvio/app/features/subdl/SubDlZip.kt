package com.nuvio.app.features.subdl

/** One file of a ZIP archive, already decompressed. */
internal class ZipEntryData(
    val name: String,
    val bytes: ByteArray,
)

/** Inflates raw DEFLATE data (no zlib/gzip header), at most [maxOutputBytes]. */
internal expect fun inflateRawDeflate(bytes: ByteArray, maxOutputBytes: Int): ByteArray

/**
 * Minimal ZIP reader for the SubDL archives: reads the central directory and returns the files
 * stored or deflated (the only methods subtitle archives use). Directories, encrypted entries and
 * unsupported methods are skipped; a broken archive gives an empty list.
 */
internal fun readZipEntries(
    archive: ByteArray,
    accept: (String) -> Boolean = { true },
    maxEntryBytes: Int = 8 * 1024 * 1024,
): List<ZipEntryData> {
    val end = findEndOfCentralDirectory(archive) ?: return emptyList()
    val entryCount = archive.u16(end + 10)
    var offset = archive.u32(end + 16).toInt()
    val entries = mutableListOf<ZipEntryData>()
    repeat(entryCount) {
        if (offset < 0 || offset + 46 > archive.size || archive.u32(offset) != CENTRAL_HEADER) return entries
        val flags = archive.u16(offset + 8)
        val method = archive.u16(offset + 10)
        val compressedSize = archive.u32(offset + 20).toInt()
        val size = archive.u32(offset + 24).toInt()
        val nameLength = archive.u16(offset + 28)
        val extraLength = archive.u16(offset + 30)
        val commentLength = archive.u16(offset + 32)
        val localOffset = archive.u32(offset + 42).toInt()
        val nameEnd = offset + 46 + nameLength
        if (nameEnd > archive.size) return entries
        val name = archive.copyOfRange(offset + 46, nameEnd).decodeToString()
        offset = nameEnd + extraLength + commentLength

        val encrypted = flags and 1 != 0
        if (name.endsWith('/') || encrypted || size > maxEntryBytes || !accept(name)) return@repeat
        val data = localEntryData(archive, localOffset, compressedSize) ?: return@repeat
        val bytes = when (method) {
            0 -> data
            8 -> runCatching { inflateRawDeflate(data, maxEntryBytes) }.getOrNull() ?: return@repeat
            else -> return@repeat
        }
        entries += ZipEntryData(name, bytes)
    }
    return entries
}

private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50L
private const val CENTRAL_HEADER = 0x02014b50L
private const val LOCAL_HEADER = 0x04034b50L

private fun findEndOfCentralDirectory(archive: ByteArray): Int? {
    if (archive.size < 22) return null
    // The record is 22 bytes plus a comment of at most 65535 bytes.
    val lowest = maxOf(0, archive.size - 22 - 0xFFFF)
    for (index in archive.size - 22 downTo lowest) {
        if (archive.u32(index) == END_OF_CENTRAL_DIRECTORY) return index
    }
    return null
}

private fun localEntryData(archive: ByteArray, localOffset: Int, compressedSize: Int): ByteArray? {
    if (localOffset < 0 || localOffset + 30 > archive.size || archive.u32(localOffset) != LOCAL_HEADER) return null
    val start = localOffset + 30 + archive.u16(localOffset + 26) + archive.u16(localOffset + 28)
    val end = start + compressedSize
    if (compressedSize < 0 || end > archive.size) return null
    return archive.copyOfRange(start, end)
}

private fun ByteArray.u16(index: Int): Int =
    (this[index].toInt() and 0xFF) or ((this[index + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.u32(index: Int): Long =
    u16(index).toLong() or (u16(index + 2).toLong() shl 16)
