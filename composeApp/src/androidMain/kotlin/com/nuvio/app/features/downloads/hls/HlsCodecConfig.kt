@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.downloads.hls

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.container.NalUnitUtil
import androidx.media3.muxer.Mp4Muxer
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Formats [Mp4Muxer] can store; anything else makes the download an MKV file. */
internal fun Format.isMp4Compatible(): Boolean {
    val mime = sampleMimeType ?: return false
    return mime in Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES || mime in Mp4Muxer.SUPPORTED_AUDIO_SAMPLE_MIME_TYPES
}

internal class MatroskaCodec(
    val codecId: String,
    val codecPrivate: ByteArray?,
    /** H.264/H.265 samples come with start codes and Matroska wants length prefixes. */
    val convertAnnexB: Boolean = false,
    val codecDelayNs: Long? = null,
    val seekPreRollNs: Long? = null,
)

/** Matroska codec of an extractor [Format], or null when the writer does not support it. */
internal fun Format.matroskaCodec(): MatroskaCodec? = when (sampleMimeType) {
    MimeTypes.VIDEO_H264 -> MatroskaCodec("V_MPEG4/ISO/AVC", avcDecoderConfiguration(initializationData), convertAnnexB = true)
    MimeTypes.VIDEO_H265, MimeTypes.VIDEO_DOLBY_VISION ->
        hevcDecoderConfiguration(initializationData)?.let { MatroskaCodec("V_MPEGH/ISO/HEVC", it, convertAnnexB = true) }
    MimeTypes.VIDEO_AV1 -> MatroskaCodec("V_AV1", initializationData.firstOrNull())
    MimeTypes.VIDEO_VP9 -> MatroskaCodec("V_VP9", null)
    MimeTypes.AUDIO_AAC -> MatroskaCodec("A_AAC", initializationData.firstOrNull())
    MimeTypes.AUDIO_AC3 -> MatroskaCodec("A_AC3", null)
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> MatroskaCodec("A_EAC3", null)
    MimeTypes.AUDIO_MPEG -> MatroskaCodec("A_MPEG/L3", null)
    MimeTypes.AUDIO_MPEG_L2 -> MatroskaCodec("A_MPEG/L2", null)
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> MatroskaCodec("A_DTS", null)
    MimeTypes.AUDIO_TRUEHD -> MatroskaCodec("A_TRUEHD", null)
    MimeTypes.AUDIO_FLAC -> MatroskaCodec("A_FLAC", initializationData.firstOrNull())
    MimeTypes.AUDIO_OPUS -> MatroskaCodec(
        codecId = "A_OPUS",
        codecPrivate = initializationData.firstOrNull(),
        codecDelayNs = initializationData.getOrNull(1)?.littleEndianLong(),
        seekPreRollNs = initializationData.getOrNull(2)?.littleEndianLong(),
    )
    else -> null
}

/** Splits Annex-B data ("00 00 01" / "00 00 00 01" start codes) into its NAL units. */
internal fun splitAnnexB(data: ByteArray): List<ByteArray> {
    val units = mutableListOf<ByteArray>()
    var index = 0
    var start = -1
    while (index + 2 < data.size) {
        if (data[index].toInt() == 0 && data[index + 1].toInt() == 0 && data[index + 2].toInt() == 1) {
            if (start >= 0) units += data.copyOfRange(start, trimTrailingZeros(data, start, index))
            index += 3
            start = index
        } else {
            index++
        }
    }
    if (start >= 0 && start < data.size) units += data.copyOfRange(start, data.size)
    if (start < 0 && data.isNotEmpty()) units += data
    return units.filter { it.isNotEmpty() }
}

private fun trimTrailingZeros(data: ByteArray, start: Int, end: Int): Int {
    var limit = end
    while (limit > start && data[limit - 1].toInt() == 0) limit--
    return limit
}

/** Annex-B access unit → 4-byte length prefixed NAL units. */
internal fun annexBToLengthPrefixed(data: ByteArray): ByteArray {
    val units = splitAnnexB(data)
    val buffer = ByteBuffer.allocate(units.sumOf { it.size + 4 })
    units.forEach { unit ->
        buffer.putInt(unit.size)
        buffer.put(unit)
    }
    return buffer.array()
}

/** AVCDecoderConfigurationRecord (ISO/IEC 14496-15 5.3.3.1) from the SPS/PPS in [initializationData]. */
internal fun avcDecoderConfiguration(initializationData: List<ByteArray>): ByteArray? {
    val units = initializationData.flatMap(::splitAnnexB)
    val sps = units.filter { it[0].toInt() and 0x1F == 7 }
    val pps = units.filter { it[0].toInt() and 0x1F == 8 }
    val first = sps.firstOrNull()?.takeIf { it.size >= 4 } ?: return null
    val output = ByteArrayOutputStream()
    output.write(1)
    output.write(first[1].toInt())
    output.write(first[2].toInt())
    output.write(first[3].toInt())
    output.write(0xFF) // 4-byte NAL lengths
    output.write(0xE0 or sps.size.coerceAtMost(31))
    sps.take(31).forEach { output.writeShortPrefixed(it) }
    output.write(pps.size.coerceAtMost(255))
    pps.take(255).forEach { output.writeShortPrefixed(it) }
    return output.toByteArray()
}

/** HEVCDecoderConfigurationRecord (ISO/IEC 14496-15 8.3.3.1) from the VPS/SPS/PPS in [initializationData]. */
internal fun hevcDecoderConfiguration(initializationData: List<ByteArray>): ByteArray? {
    val units = initializationData.flatMap(::splitAnnexB).filter { it.size >= 2 }
    fun type(unit: ByteArray) = (unit[0].toInt() shr 1) and 0x3F
    val vps = units.firstOrNull { type(it) == 32 } ?: return null
    val sps = units.firstOrNull { type(it) == 33 } ?: return null
    val unescapedVps = vps.copyOf().let { it.copyOf(NalUnitUtil.unescapeStream(it, it.size)) }
    if (unescapedVps.size < 18) return null
    val spsData = runCatching { NalUnitUtil.parseH265SpsNalUnit(sps, 0, sps.size, null) }.getOrNull() ?: return null

    val output = ByteArrayOutputStream()
    output.write(1)
    // general_profile_space, tier, profile_idc, compatibility flags, constraint flags and level
    // are the profile_tier_level of the VPS (bytes 6..17 after the 2-byte NAL header).
    output.write(unescapedVps, 6, 12)
    output.write(0xF0)
    output.write(0x00)
    output.write(0xFC)
    output.write(0xFC or spsData.chromaFormatIdc)
    output.write(0xF8 or spsData.bitDepthLumaMinus8)
    output.write(0xF8 or spsData.bitDepthChromaMinus8)
    output.write(0x00)
    output.write(0x00)
    output.write(0x0F) // 4-byte NAL lengths
    val arrays = units.groupBy(::type).filterKeys { it in 32..34 || it == 39 }
    output.write(arrays.size)
    arrays.toSortedMap().forEach { (nalType, list) ->
        output.write(0x80 or nalType)
        output.write(list.size shr 8)
        output.write(list.size and 0xFF)
        list.forEach { output.writeShortPrefixed(it) }
    }
    return output.toByteArray()
}

private fun ByteArrayOutputStream.writeShortPrefixed(unit: ByteArray) {
    write(unit.size shr 8)
    write(unit.size and 0xFF)
    write(unit)
}

private fun ByteArray.littleEndianLong(): Long? {
    if (size < 8) return null
    var value = 0L
    for (index in 7 downTo 0) value = (value shl 8) or (this[index].toLong() and 0xFF)
    return value
}
