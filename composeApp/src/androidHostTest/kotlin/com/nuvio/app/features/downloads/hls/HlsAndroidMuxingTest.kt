package com.nuvio.app.features.downloads.hls

import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HlsAndroidMuxingTest {

    private class Element(val id: Long, val start: Long, val dataStart: Long, val size: Long)

    /** Reads the EBML elements found in [data] between [from] and [to]. */
    private fun elements(data: ByteArray, from: Long, to: Long): List<Element> {
        val result = mutableListOf<Element>()
        var position = from
        while (position < to) {
            val start = position
            val (id, idLength) = readVint(data, position, keepMarker = true)
            position += idLength
            val (size, sizeLength) = readVint(data, position, keepMarker = false)
            position += sizeLength
            result += Element(id, start, position, size)
            position += size
        }
        assertEquals(to, position, "elements must fill their parent exactly")
        return result
    }

    private fun readVint(data: ByteArray, position: Long, keepMarker: Boolean): Pair<Long, Int> {
        val first = data[position.toInt()].toInt() and 0xFF
        val length = Integer.numberOfLeadingZeros(first) - 23
        var value = if (keepMarker) first.toLong() else (first and (0xFF shr length)).toLong()
        for (index in 1 until length) value = (value shl 8) or (data[position.toInt() + index].toLong() and 0xFF)
        return value to length
    }

    private fun Element.children(data: ByteArray) = elements(data, dataStart, dataStart + size)

    private fun Element.uint(data: ByteArray): Long =
        (0 until size.toInt()).fold(0L) { value, index -> (value shl 8) or (data[(dataStart + index).toInt()].toLong() and 0xFF) }

    private fun Element.string(data: ByteArray) = String(data, dataStart.toInt(), size.toInt())

    @Test
    fun writesAWellFormedMatroskaFile() {
        val file = File.createTempFile("hls-test", ".mkv")
        try {
            MatroskaWriter(file).use { writer ->
                writer.start(
                    listOf(
                        MatroskaWriter.Track(1, MatroskaWriter.TrackType.Video, "V_MPEG4/ISO/AVC", byteArrayOf(1, 2), width = 1920, height = 1080, isDefault = true),
                        MatroskaWriter.Track(2, MatroskaWriter.TrackType.Audio, "A_EAC3", language = "ita", name = "Italiano", sampleRate = 48_000, channels = 6, isDefault = true),
                        MatroskaWriter.Track(3, MatroskaWriter.TrackType.Subtitle, "S_TEXT/UTF8", language = "en", isForced = true),
                    ),
                )
                writer.writeSample(1, 0L, byteArrayOf(9, 9), keyFrame = true)
                writer.writeSample(2, 10_000L, byteArrayOf(7), keyFrame = true)
                writer.writeSample(3, 500_000L, "Ciao".encodeToByteArray(), keyFrame = true, durationUs = 1_000_000L)
                writer.writeSample(1, 40_000L, byteArrayOf(8), keyFrame = false)
                writer.writeSample(1, 2_000_000L, byteArrayOf(6), keyFrame = true)
                writer.finish()
            }
            val data = file.readBytes()

            val top = elements(data, 0, data.size.toLong())
            assertEquals(listOf(0x1A45DFA3L, 0x18538067L), top.map { it.id })
            assertEquals("matroska", top[0].children(data).first { it.id == 0x4282L }.string(data))

            val segment = top[1]
            val children = segment.children(data)
            val ids = children.map { it.id }
            assertEquals(listOf(0x114D9B74L, 0xECL, 0x1549A966L, 0x1654AE6BL, 0x1F43B675L, 0x1F43B675L, 0x1C53BB6BL), ids)

            // The SeekHead points at the right elements.
            val seeks = children[0].children(data).map { seek ->
                val parts = seek.children(data)
                val id = parts.first { it.id == 0x53ABL }.uint(data)
                val position = parts.first { it.id == 0x53ACL }.uint(data)
                id to children.first { it.start == segment.dataStart + position }.id
            }
            seeks.forEach { (expected, found) -> assertEquals(expected, found) }

            val tracks = children.first { it.id == 0x1654AE6BL }.children(data)
            assertEquals(3, tracks.size)
            val audio = tracks[1].children(data)
            assertEquals("A_EAC3", audio.first { it.id == 0x86L }.string(data))
            assertEquals("ita", audio.first { it.id == 0x22B59CL }.string(data))
            assertEquals("eng", MatroskaWriter.matroskaLanguage("en-US"))

            // A new cluster starts at the second video key frame.
            val clusters = children.filter { it.id == 0x1F43B675L }
            assertEquals(listOf(0L, 2_000L), clusters.map { cluster -> cluster.children(data).first { it.id == 0xE7L }.uint(data) })
            val firstCluster = clusters[0].children(data)
            assertTrue(firstCluster.any { it.id == 0xA0L }, "subtitles are BlockGroups with a duration")
            assertEquals(3, firstCluster.count { it.id == 0xA3L })
        } finally {
            file.delete()
        }
    }

    @Test
    fun encodesEbmlSizes() {
        assertContentEquals(byteArrayOf(0x81.toByte()), MatroskaWriter.vint(1))
        assertContentEquals(byteArrayOf(0x40, 0x7F), MatroskaWriter.vint(127))
        assertContentEquals(byteArrayOf(0x40, 0x80.toByte()), MatroskaWriter.vint(128))
    }

    @Test
    fun stripsId3AndReadsTheTransportStreamTimestamp() {
        val owner = "com.apple.streaming.transportStreamTimestamp".encodeToByteArray()
        val pts = 8_589_934_000L and 0x1FFFFFFFFL
        val priv = owner + byteArrayOf(0) + ByteBuffer.allocate(8).putLong(pts).array()
        val frame = "PRIV".encodeToByteArray() + ByteBuffer.allocate(4).putInt(priv.size).array() + byteArrayOf(0, 0) + priv
        val tag = "ID3".encodeToByteArray() + byteArrayOf(4, 0, 0) + syncSafe(frame.size) + frame
        val adts = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte())

        val (timestamp, payload) = stripId3(tag + adts)

        assertEquals(pts, timestamp)
        assertContentEquals(adts, payload)
        assertEquals(HlsSegmentFormat.PackedAac, sniffSegmentFormat(tag + adts, hasInitSegment = false))
    }

    @Test
    fun recognisesSegmentFormats() {
        assertEquals(HlsSegmentFormat.Ts, sniffSegmentFormat(ByteArray(188).also { it[0] = 0x47 }, false))
        assertEquals(HlsSegmentFormat.Fmp4, sniffSegmentFormat(ByteArray(16), true))
        assertEquals(HlsSegmentFormat.Fmp4, sniffSegmentFormat(byteArrayOf(0, 0, 0, 16) + "styp".encodeToByteArray() + ByteArray(8), false))
        assertEquals(HlsSegmentFormat.PackedAc3, sniffSegmentFormat(byteArrayOf(0x0B, 0x77, 1, 2), false))
        assertEquals(HlsSegmentFormat.WebVtt, sniffSegmentFormat("WEBVTT\n\n".encodeToByteArray(), false))
    }

    @Test
    fun buildsAvcConfigurationAndLengthPrefixedSamples() {
        val sps = byteArrayOf(0x67, 0x64, 0x00, 0x28, 0x11)
        val pps = byteArrayOf(0x68, 0x22)
        val config = avcDecoderConfiguration(listOf(byteArrayOf(0, 0, 0, 1) + sps, byteArrayOf(0, 0, 0, 1) + pps))!!

        assertContentEquals(
            byteArrayOf(1, 0x64, 0x00, 0x28, 0xFF.toByte(), 0xE1.toByte(), 0, 5) + sps + byteArrayOf(1, 0, 2) + pps,
            config,
        )
        assertContentEquals(
            byteArrayOf(0, 0, 0, 2, 0x09, 0x10, 0, 0, 0, 3, 0x65, 0x01, 0x02),
            annexBToLengthPrefixed(byteArrayOf(0, 0, 0, 1, 0x09, 0x10, 0, 0, 1, 0x65, 0x01, 0x02)),
        )
    }

    private fun syncSafe(value: Int) = byteArrayOf(
        (value shr 21 and 0x7F).toByte(),
        (value shr 14 and 0x7F).toByte(),
        (value shr 7 and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )
}
