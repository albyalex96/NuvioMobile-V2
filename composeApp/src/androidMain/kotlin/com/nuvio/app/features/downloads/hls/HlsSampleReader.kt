@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.app.features.downloads.hls

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.ts.Ac3Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import kotlinx.serialization.Serializable

/** Container of the media segments of one HLS track, as saved by the downloader. */
@Serializable
internal enum class HlsSegmentFormat { Ts, Fmp4, PackedAac, PackedAc3, PackedMp3, WebVtt, Unsupported }

internal class HlsSample(
    val trackId: Int,
    val timeUs: Long,
    val flags: Int,
    val data: ByteArray,
) {
    val isKeyFrame: Boolean get() = flags and C.BUFFER_FLAG_KEY_FRAME != 0
}

/**
 * Reads the samples of a downloaded HLS track file (all the segments of one discontinuity group
 * appended together) through the Media3 extractors. MPEG-TS timestamps are absolute (not shifted
 * to zero) so separate renditions keep their sync; packed audio starts at zero.
 */
internal class HlsSampleReader(file: File, format: HlsSegmentFormat) : Closeable {
    class Track(val id: Int, val type: Int) {
        var format: Format? = null
            internal set
        internal var sawSample = false
    }

    private val access = RandomAccessFile(file, "r")
    private val extractor: Extractor = when (format) {
        HlsSegmentFormat.Ts -> TsExtractor(
            TsExtractor.MODE_SINGLE_PMT,
            TimestampAdjuster(TimestampAdjuster.MODE_NO_OFFSET),
            DefaultTsPayloadReaderFactory(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    // Access units are split at the AUD NAL units; slice headers are used only when
                    // the stream has none (using both splits every picture into several samples).
                    if (hasH264AccessUnitDelimiters(file)) 0 else DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
            ),
        )
        HlsSegmentFormat.Fmp4 -> FragmentedMp4Extractor(0)
        HlsSegmentFormat.PackedAac -> AdtsExtractor()
        HlsSegmentFormat.PackedAc3 -> Ac3Extractor()
        HlsSegmentFormat.PackedMp3 -> Mp3Extractor(Mp3Extractor.FLAG_DISABLE_ID3_METADATA)
        HlsSegmentFormat.WebVtt, HlsSegmentFormat.Unsupported -> error("Not a media track: $format")
    }
    private val outputs = linkedMapOf<Int, SampleOutput>()
    private val queue = ArrayDeque<HlsSample>()
    private val positionHolder = PositionHolder()
    private var input: ExtractorInput = inputAt(0L)
    private var ended = false

    val tracks: List<Track>
        get() = outputs.values.map { it.track }

    init {
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput =
                outputs.getOrPut(id) { SampleOutput(Track(id, type)) }

            override fun endTracks() = Unit

            override fun seekMap(seekMap: SeekMap) = Unit
        })
    }

    /** The next sample of any track, in file order; null at the end of the file. */
    fun next(): HlsSample? {
        while (queue.isEmpty()) {
            if (ended) return null
            when (extractor.read(input, positionHolder)) {
                Extractor.RESULT_END_OF_INPUT -> ended = true
                Extractor.RESULT_SEEK -> input = inputAt(positionHolder.position)
            }
        }
        return queue.removeFirst()
    }

    override fun close() {
        extractor.release()
        access.close()
    }

    private fun inputAt(position: Long): ExtractorInput {
        access.seek(position)
        val reader = DataReader { buffer, offset, length ->
            if (length == 0) 0 else access.read(buffer, offset, length).let { if (it < 0) C.RESULT_END_OF_INPUT else it }
        }
        return DefaultExtractorInput(reader, position, C.LENGTH_UNSET.toLong())
    }

    private inner class SampleOutput(val track: Track) : TrackOutput {
        private var buffer = ByteArray(64 * 1024)
        private var length = 0

        override fun format(format: Format) {
            track.format = format
        }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            ensureCapacity(this.length + length)
            val read = input.read(buffer, this.length, length)
            if (read == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput) return C.RESULT_END_OF_INPUT
                throw EOFException()
            }
            this.length += read
            return read
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            ensureCapacity(this.length + length)
            data.readBytes(buffer, this.length, length)
            this.length += length
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            val start = length - offset - size
            if (start < 0 || size < 0) {
                length = 0
                return
            }
            val sample = buffer.copyOfRange(start, start + size)
            // The last [offset] bytes already belong to the next sample.
            System.arraycopy(buffer, start + size, buffer, 0, offset)
            length = offset
            track.sawSample = true
            queue.addLast(HlsSample(track.id, timeUs, flags, sample))
        }

        private fun ensureCapacity(required: Int) {
            if (required <= buffer.size) return
            buffer = buffer.copyOf(maxOf(required, buffer.size * 2))
        }
    }
}

/** Whether the first megabytes of an MPEG-TS file contain H.264 access unit delimiters (NAL type 9). */
internal fun hasH264AccessUnitDelimiters(file: File, scanBytes: Int = 2 * 1024 * 1024): Boolean {
    val data = RandomAccessFile(file, "r").use { access ->
        ByteArray(minOf(access.length(), scanBytes.toLong()).toInt()).also { access.readFully(it) }
    }
    for (index in 0 until data.size - 4) {
        if (data[index].toInt() == 0 && data[index + 1].toInt() == 0 && data[index + 2].toInt() == 1 &&
            data[index + 3].toInt() and 0x1F == 9 && data[index + 3].toInt() and 0x80 == 0
        ) {
            return true
        }
    }
    return false
}
