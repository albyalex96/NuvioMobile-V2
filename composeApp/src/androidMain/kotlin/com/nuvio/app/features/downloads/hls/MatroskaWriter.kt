package com.nuvio.app.features.downloads.hls

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random

/**
 * Minimal Matroska (MKV) writer: one Segment with SeekHead, Info, Tracks, Clusters of SimpleBlocks
 * (BlockGroups with a duration for subtitles) and Cues on the video key frames. Samples must be
 * written roughly in time order; timestamps are stored in milliseconds.
 */
internal class MatroskaWriter(file: File) : Closeable {
    enum class TrackType(val value: Long) { Video(1), Audio(2), Subtitle(17) }

    class Track(
        val number: Int,
        val type: TrackType,
        val codecId: String,
        val codecPrivate: ByteArray? = null,
        val language: String? = null,
        val name: String? = null,
        val isDefault: Boolean = false,
        val isForced: Boolean = false,
        val width: Int? = null,
        val height: Int? = null,
        val sampleRate: Int? = null,
        val channels: Int? = null,
        val codecDelayNs: Long? = null,
        val seekPreRollNs: Long? = null,
    )

    private val output = RandomAccessFile(file, "rw").apply { setLength(0L) }
    private var segmentSizePosition = 0L
    private var segmentDataStart = 0L
    private var seekHeadPosition = 0L
    private var durationPosition = 0L
    private var infoPosition = 0L
    private var tracksPosition = 0L
    private var videoTrackNumber: Int? = null

    private val cluster = ByteArrayOutputStream()
    private var clusterTimeMs = -1L
    private var clusterHasVideo = false
    private val cues = mutableListOf<Pair<Long, Long>>() // time ms, cluster position (relative)
    private var maxTimeUs = 0L

    fun start(tracks: List<Track>) {
        videoTrackNumber = tracks.firstOrNull { it.type == TrackType.Video }?.number
        write(element(0x1A45DFA3, master {
            uint(0x4286, 1)
            uint(0x42F7, 1)
            uint(0x42F2, 4)
            uint(0x42F3, 8)
            string(0x4282, "matroska")
            uint(0x4287, 4)
            uint(0x4285, 2)
        }))
        // Segment with an 8-byte size patched in finish().
        write(id(0x18538067))
        segmentSizePosition = output.filePointer
        write(byteArrayOf(0x01, 0, 0, 0, 0, 0, 0, 0))
        segmentDataStart = output.filePointer

        seekHeadPosition = output.filePointer
        write(voidElement(SEEK_HEAD_RESERVED))

        infoPosition = output.filePointer - segmentDataStart
        val info = master {
            uint(0x2AD7B1, 1_000_000)
            binary(0x73A4, Random.nextBytes(16))
            string(0x4D80, "Nuvio")
            string(0x5741, "Nuvio")
        }
        // Info is written by hand to know where the Duration float is.
        val durationElement = id(0x4489) + vint(8) + ByteArray(8)
        write(id(0x1549A966) + vint((info.size + durationElement.size).toLong()) + info)
        durationPosition = output.filePointer + id(0x4489).size + 1
        write(durationElement)

        tracksPosition = output.filePointer - segmentDataStart
        write(element(0x1654AE6B, master {
            tracks.forEach { track -> raw(element(0xAE, trackEntry(track))) }
        }))
    }

    /** [timeUs] is the presentation time; [durationUs] is required for subtitle blocks. */
    fun writeSample(trackNumber: Int, timeUs: Long, data: ByteArray, keyFrame: Boolean, durationUs: Long? = null) {
        val timeMs = Math.floorDiv(timeUs, 1000L)
        maxTimeUs = maxOf(maxTimeUs, timeUs + (durationUs ?: 0L))
        val isVideo = trackNumber == videoTrackNumber
        val relative = timeMs - clusterTimeMs
        val needsNewCluster = clusterTimeMs < 0 ||
            relative !in Short.MIN_VALUE..Short.MAX_VALUE ||
            (isVideo && keyFrame && clusterHasVideo) ||
            (videoTrackNumber == null && relative > 5_000) ||
            cluster.size() > MAX_CLUSTER_BYTES
        if (needsNewCluster) {
            flushCluster()
            clusterTimeMs = maxOf(timeMs, 0L)
            if (videoTrackNumber == null || (isVideo && keyFrame)) {
                cues += clusterTimeMs to (output.filePointer - segmentDataStart)
            }
        }
        if (isVideo) clusterHasVideo = true
        val blockTime = (timeMs - clusterTimeMs).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        val header = vint(trackNumber.toLong()) + byteArrayOf((blockTime shr 8).toByte(), blockTime.toByte())
        if (durationUs != null) {
            val block = header + byteArrayOf(0) + data
            cluster.write(element(0xA0, element(0xA1, block) + uintElement(0x9B, (durationUs / 1000L).coerceAtLeast(1L))))
        } else {
            val flags = if (keyFrame) 0x80 else 0x00
            cluster.write(element(0xA3, header + byteArrayOf(flags.toByte()) + data))
        }
    }

    fun finish(durationUs: Long? = null) {
        flushCluster()
        val cuesPosition = output.filePointer - segmentDataStart
        if (cues.isNotEmpty()) {
            write(element(0x1C53BB6B, master {
                cues.forEach { (timeMs, position) ->
                    raw(element(0xBB, master {
                        uint(0xB3, timeMs)
                        raw(element(0xB7, master {
                            uint(0xF7, (videoTrackNumber ?: 1).toLong())
                            uint(0xF1, position)
                        }))
                    }))
                }
            }))
        }
        val end = output.filePointer

        output.seek(segmentSizePosition)
        output.write(byteArrayOf(0x01) + longBytes(end - segmentDataStart, 7))

        output.seek(durationPosition)
        output.writeDouble((durationUs ?: maxTimeUs) / 1000.0)

        val seekHead = element(0x114D9B74, master {
            raw(seekEntry(0x1549A966, infoPosition))
            raw(seekEntry(0x1654AE6B, tracksPosition))
            if (cues.isNotEmpty()) raw(seekEntry(0x1C53BB6B, cuesPosition))
        })
        output.seek(seekHeadPosition)
        output.write(seekHead)
        output.write(voidElement(SEEK_HEAD_RESERVED - seekHead.size))
        output.seek(end)
    }

    override fun close() {
        output.close()
    }

    private fun flushCluster() {
        if (clusterTimeMs < 0 || cluster.size() == 0) return
        write(element(0x1F43B675, uintElement(0xE7, clusterTimeMs) + cluster.toByteArray()))
        cluster.reset()
        clusterHasVideo = false
    }

    private fun trackEntry(track: Track): ByteArray = master {
        uint(0xD7, track.number.toLong())
        uint(0x73C5, track.number.toLong() * 7919L + Random.nextLong(1L, 1L shl 40))
        uint(0x83, track.type.value)
        uint(0xB9, 1)
        uint(0x88, if (track.isDefault) 1 else 0)
        if (track.isForced) uint(0x55AA, 1)
        uint(0x9C, 0)
        track.name?.let { string(0x536E, it) }
        string(0x22B59C, matroskaLanguage(track.language))
        track.language?.let { string(0x22B59D, it) }
        string(0x86, track.codecId)
        track.codecPrivate?.let { binary(0x63A2, it) }
        track.codecDelayNs?.let { uint(0x56AA, it) }
        track.seekPreRollNs?.let { uint(0x56BB, it) }
        if (track.width != null && track.height != null) {
            raw(element(0xE0, master {
                uint(0xB0, track.width.toLong())
                uint(0xBA, track.height.toLong())
            }))
        }
        if (track.sampleRate != null || track.channels != null) {
            raw(element(0xE1, master {
                track.sampleRate?.let { float(0xB5, it.toDouble()) }
                track.channels?.let { uint(0x9F, it.toLong()) }
            }))
        }
    }

    private fun seekEntry(elementId: Long, position: Long): ByteArray =
        element(0x4DBB, binaryElement(0x53AB, id(elementId)) + uintElement(0x53AC, position))

    private fun write(bytes: ByteArray) = output.write(bytes)

    private class Master {
        val bytes = ByteArrayOutputStream()
        fun raw(data: ByteArray) = bytes.write(data)
        fun uint(id: Long, value: Long) = raw(uintElement(id, value))
        fun float(id: Long, value: Double) = raw(element(id, longBytes(java.lang.Double.doubleToLongBits(value), 8)))
        fun string(id: Long, value: String) = raw(element(id, value.encodeToByteArray()))
        fun binary(id: Long, value: ByteArray) = raw(binaryElement(id, value))
    }

    private fun master(block: Master.() -> Unit): ByteArray = Master().apply(block).bytes.toByteArray()

    companion object {
        private const val SEEK_HEAD_RESERVED = 96
        private const val MAX_CLUSTER_BYTES = 16 * 1024 * 1024

        fun id(value: Long): ByteArray {
            val length = when {
                value <= 0xFF -> 1
                value <= 0xFFFF -> 2
                value <= 0xFFFFFF -> 3
                else -> 4
            }
            return longBytes(value, length)
        }

        /** EBML variable-size integer, the shortest form that can hold [value]. */
        fun vint(value: Long): ByteArray {
            var length = 1
            while (length < 8 && value >= (1L shl (7 * length)) - 1L) length++
            val bytes = longBytes(value, length)
            bytes[0] = (bytes[0].toInt() or (0x80 ushr (length - 1))).toByte()
            return bytes
        }

        fun element(id: Long, data: ByteArray): ByteArray = id(id) + vint(data.size.toLong()) + data

        fun binaryElement(id: Long, data: ByteArray): ByteArray = element(id, data)

        fun uintElement(id: Long, value: Long): ByteArray {
            var length = 1
            while (length < 8 && (value ushr (8 * length)) != 0L) length++
            return element(id, longBytes(value, length))
        }

        fun voidElement(totalSize: Int): ByteArray {
            require(totalSize >= 9) { "Void element too small" }
            // ID (1 byte) + 8-byte size: the payload fills the rest exactly.
            val payload = totalSize - 9
            return byteArrayOf(0xEC.toByte(), 0x01) + longBytes(payload.toLong(), 7) + ByteArray(payload)
        }

        fun longBytes(value: Long, length: Int): ByteArray =
            ByteArray(length) { index -> (value ushr (8 * (length - 1 - index))).toByte() }

        /** Legacy Language element: ISO 639-2 code ("ita"), "und" when unknown. */
        fun matroskaLanguage(tag: String?): String {
            if (tag.isNullOrBlank()) return "und"
            val primary = tag.substringBefore('-').substringBefore('_').lowercase()
            return runCatching { java.util.Locale(primary).isO3Language }.getOrNull()
                ?.takeIf { it.length == 3 } ?: if (primary.length == 3) primary else "und"
        }
    }
}
