package com.nuvio.app.features.player.seekthumb

import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.TsExtractor
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.math.BigInteger
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * HLS previews (not in Nuvio Arc): the lowest-bitrate video variant, and for a position the segment holding it. Only
 * the head of that segment is read (its first keyframe opens it), demuxed with Media3's TS / fragmented MP4
 * extractors and handed to the same keyframe decoder as the single-file path. VOD playlists only; AES-128 segments
 * are decrypted, SAMPLE-AES and DRM are refused.
 */
@OptIn(UnstableApi::class)
internal class HlsThumbSource private constructor(
    private val http: HlsHttp,
    private val playlist: HlsMediaPlaylist,
    private val variantLabel: String,
) : ThumbSource {
    private companion object {
        /** Head of a segment read for its first keyframe; the whole segment is read only when this has none. */
        const val SEGMENT_HEAD_BYTES = 1024 * 1024
        const val MAX_SEGMENT_BYTES = 16 * 1024 * 1024
        const val MAX_PLAYLIST_BYTES = 8 * 1024 * 1024
        const val MAX_INIT_BYTES = 2 * 1024 * 1024
    }

    private val segments = playlist.segments
    private val initSegments = HashMap<String, ByteArray>()
    private val keys = HashMap<String, ByteArray>()
    /** The decoder keeps its context while the track object stays the same, so an unchanged format reuses it. */
    private var lastTrack: VideoTrack? = null

    override var description: String = "HLS $variantLabel segments=${segments.size}"
        private set
    override var videoWidth: Int = 0
        private set
    override var videoHeight: Int = 0
        private set

    val requests: Int get() = http.requests
    val bytes: Long get() = http.bytes

    override fun keyFor(positionMs: Long): Long {
        val i = segmentAt(positionMs * 1000L)
        return if (i < 0) -1L else segments[i].relativeStartTimeUs / 1000L
    }

    private fun segmentAt(timeUs: Long): Int {
        if (segments.isEmpty()) return -1
        var lo = 0
        var hi = segments.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (segments[mid].relativeStartTimeUs <= timeUs) lo = mid else hi = mid - 1
        }
        return lo
    }

    override fun fetch(key: Long): ThumbKeyframe? {
        val index = segmentAt(key * 1000L)
        if (index < 0) return null
        val segment = segments[index]
        if (segment.drmInitData != null) throw UnsupportedMediaException("DRM-protected HLS")
        val url = UriUtil.resolve(playlist.baseUri, segment.url)
        val init = segment.initializationSegment?.let { initSegment(it) }
        val whole = segment.byteRangeLength != C.LENGTH_UNSET.toLong() &&
            segment.byteRangeLength <= SEGMENT_HEAD_BYTES
        var data = segmentBytes(segment, index, url, if (whole) Int.MAX_VALUE else SEGMENT_HEAD_BYTES)
        var found = firstKeyframe(init, data.bytes)
        if (found == null && data.partial) {
            data = segmentBytes(segment, index, url, MAX_SEGMENT_BYTES)
            found = firstKeyframe(init, data.bytes)
        }
        found ?: return null
        val (format, sample) = found
        val track = videoTrackOf(format).let { fresh ->
            lastTrack?.takeIf { it.sameAs(fresh) } ?: fresh.also { lastTrack = it }
        }
        videoWidth = track.width
        videoHeight = track.height
        description = "HLS $variantLabel ${track.codec} ${track.width}x${track.height} segments=${segments.size}"
        val au = tapSampleToAnnexB(sample, track) ?: return null
        return ThumbKeyframe(segment.relativeStartTimeUs / 1000L, ThumbAu.of(au), track)
    }

    override fun cancel() = http.cancel()

    override fun close() = http.close()

    private class SegmentBytes(val bytes: ByteArray, val partial: Boolean)

    private fun segmentBytes(segment: HlsMediaPlaylist.Segment, index: Int, url: String, limit: Int): SegmentBytes {
        val offset = segment.byteRangeOffset
        val declared = segment.byteRangeLength.takeIf { it != C.LENGTH_UNSET.toLong() }
        val want = minOf(limit.toLong(), declared ?: Long.MAX_VALUE, MAX_SEGMENT_BYTES.toLong()).toInt()
        val needsRange = offset > 0 || declared != null || want < MAX_SEGMENT_BYTES
        val raw = http.get(url, if (needsRange) offset until offset + want else null, want)
        val keyUri = segment.fullSegmentEncryptionKeyUri
        val bytes = if (keyUri != null) {
            decryptAes128(raw, keyUri, segment.encryptionIV, playlist.mediaSequence + index)
        } else {
            raw
        }
        val partial = raw.size >= want && want < (declared ?: Long.MAX_VALUE) && want < MAX_SEGMENT_BYTES
        return SegmentBytes(bytes, partial)
    }

    private fun initSegment(init: HlsMediaPlaylist.Segment): ByteArray {
        val url = UriUtil.resolve(playlist.baseUri, init.url)
        val declared = init.byteRangeLength.takeIf { it != C.LENGTH_UNSET.toLong() }
        val cacheKey = "$url#${init.byteRangeOffset}#$declared"
        initSegments[cacheKey]?.let { return it }
        val want = minOf(declared ?: MAX_INIT_BYTES.toLong(), MAX_INIT_BYTES.toLong()).toInt()
        val range = if (declared != null || init.byteRangeOffset > 0) init.byteRangeOffset until init.byteRangeOffset + want else null
        val raw = http.get(url, range, want)
        val keyUri = init.fullSegmentEncryptionKeyUri
        val bytes = if (keyUri != null) decryptAes128(raw, keyUri, init.encryptionIV, playlist.mediaSequence) else raw
        initSegments[cacheKey] = bytes
        return bytes
    }

    /** AES-128 CBC over the whole segment, or over its head (CBC decrypts a prefix block by block). */
    private fun decryptAes128(data: ByteArray, keyUri: String, ivHex: String?, mediaSequence: Long): ByteArray {
        val keyUrl = UriUtil.resolve(playlist.baseUri, keyUri)
        val key = keys.getOrPut(keyUrl) { http.get(keyUrl, null, 64) }
        if (key.size != 16) throw UnsupportedMediaException("AES key of ${key.size} bytes")
        val iv = ByteArray(16)
        val ivValue = ivHex?.let {
            BigInteger(it.removePrefix("0x").removePrefix("0X"), 16).toByteArray()
        } ?: BigInteger.valueOf(mediaSequence).toByteArray()
        val n = minOf(ivValue.size, 16)
        System.arraycopy(ivValue, ivValue.size - n, iv, 16 - n, n)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val usable = data.size - data.size % 16
        return cipher.update(data, 0, usable) ?: ByteArray(0)
    }

    /** The first video keyframe of [segment] (after [init] for fragmented MP4) and the track's format. */
    private fun firstKeyframe(init: ByteArray?, segment: ByteArray): Pair<Format, ByteArray>? {
        val ts = init == null && segment.isNotEmpty() && segment[0] == 0x47.toByte()
        if (!ts && init == null && !looksLikeFragmentedMp4(segment)) {
            throw UnsupportedMediaException("HLS segment format not supported")
        }
        val data = if (init != null) init + segment else segment
        val extractor: Extractor = if (ts) {
            TsExtractor(SubtitleParser.Factory.UNSUPPORTED)
        } else {
            FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED)
        }
        val capture = KeyframeCapture()
        extractor.init(capture)
        var input = DefaultExtractorInput(ByteArrayDataReader(data, 0), 0, data.size.toLong())
        val holder = PositionHolder()
        try {
            while (capture.result == null) {
                when (extractor.read(input, holder)) {
                    Extractor.RESULT_END_OF_INPUT -> break
                    Extractor.RESULT_SEEK -> {
                        val p = holder.position
                        if (p < 0 || p >= data.size) break
                        input = DefaultExtractorInput(ByteArrayDataReader(data, p.toInt()), p, data.size.toLong())
                    }
                }
            }
        } catch (_: EOFException) {
            // The head ended inside a sample: whatever was captured before stands.
        } catch (_: ParserException) {
        } finally {
            extractor.release()
        }
        return capture.result
    }

    private fun VideoTrack.sameAs(other: VideoTrack): Boolean =
        codec == other.codec && width == other.width && height == other.height &&
            parameterSetsAnnexB.contentEquals(other.parameterSetsAnnexB)

    private fun looksLikeFragmentedMp4(b: ByteArray): Boolean {
        if (b.size < 8) return false
        val type = String(b, 4, 4, Charsets.ISO_8859_1)
        return type == "ftyp" || type == "styp" || type == "moof" || type == "sidx"
    }

    private fun videoTrackOf(format: Format): VideoTrack {
        val codec = when (format.sampleMimeType) {
            MimeTypes.VIDEO_H264 -> VideoCodec.H264
            MimeTypes.VIDEO_H265 -> VideoCodec.HEVC
            else -> throw UnsupportedMediaException("HLS video ${format.sampleMimeType} not supported")
        }
        val parameterSets = ByteArrayOutputStream()
        for (nal in format.initializationData) {
            val annexB = nal.size >= 3 && nal[0].toInt() == 0 && nal[1].toInt() == 0 &&
                (nal[2].toInt() == 1 || (nal.size >= 4 && nal[2].toInt() == 0 && nal[3].toInt() == 1))
            if (!annexB) parameterSets.write(byteArrayOf(0, 0, 0, 1))
            parameterSets.write(nal)
        }
        val colorInfo = format.colorInfo
        val colour = ContainerColour(
            transfer = when (colorInfo?.colorTransfer) {
                C.COLOR_TRANSFER_ST2084 -> 16
                C.COLOR_TRANSFER_HLG -> 18
                else -> 0
            },
            primaries = if (colorInfo?.colorSpace == C.COLOR_SPACE_BT2020) 9 else 0,
            range = when (colorInfo?.colorRange) {
                C.COLOR_RANGE_FULL -> 2
                C.COLOR_RANGE_LIMITED -> 1
                else -> 0
            },
        )
        val width = format.width.coerceAtLeast(0)
        val height = format.height.coerceAtLeast(0)
        val aspect = if (width > 0 && height > 0) width * format.pixelWidthHeightRatio.toDouble() / height else 0.0
        return VideoTrack(
            codec = codec,
            width = width,
            height = height,
            displayAspect = aspect,
            nalLengthSize = 4,
            parameterSetsAnnexB = parameterSets.toByteArray(),
            bitDepth = 8,
            colour = colour,
            dolbyVision = null,
            trackNumber = 0L,
        )
    }

    /** Keeps the first video track's format and its first keyframe sample; everything else is dropped. */
    private class KeyframeCapture : ExtractorOutput {
        var result: Pair<Format, ByteArray>? = null
        private var video: VideoCapture? = null

        override fun track(id: Int, type: Int): TrackOutput {
            if (type == C.TRACK_TYPE_VIDEO && video == null) {
                return VideoCapture { format, sample -> if (result == null) result = format to sample }.also { video = it }
            }
            return DiscardingTrackOutput()
        }

        override fun endTracks() = Unit

        override fun seekMap(seekMap: SeekMap) = Unit
    }

    private class VideoCapture(private val onKeyframe: (Format, ByteArray) -> Unit) : TrackOutput {
        private var format: Format? = null
        private var buf = ByteArray(256 * 1024)
        private var len = 0

        override fun format(format: Format) {
            this.format = format
        }

        private fun ensure(extra: Int) {
            if (len + extra <= buf.size) return
            if (len + extra > MAX_SEGMENT_BYTES) throw UnsupportedMediaException("HLS sample too large")
            buf = buf.copyOf(maxOf(buf.size * 2, len + extra))
        }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            ensure(length)
            val n = input.read(buf, len, length)
            if (n == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput) return C.RESULT_END_OF_INPUT
                throw EOFException()
            }
            len += n
            return n
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            ensure(length)
            data.readBytes(buf, len, length)
            len += length
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            val end = len - offset
            val start = end - size
            val f = format
            if (start >= 0 && f != null && flags and C.BUFFER_FLAG_KEY_FRAME != 0) {
                onKeyframe(f, buf.copyOfRange(start, end))
            }
            // Bytes up to the end of this sample are done with.
            if (end in 1..len) {
                System.arraycopy(buf, end, buf, 0, len - end)
                len -= end
            }
        }
    }

    private class ByteArrayDataReader(private val data: ByteArray, private var position: Int) : DataReader {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (position >= data.size) return C.RESULT_END_OF_INPUT
            val n = minOf(length, data.size - position)
            System.arraycopy(data, position, buffer, offset, n)
            position += n
            return n
        }
    }

    /** GETs over the playback client, counted for the log; [cancel] stops the ones under way. */
    internal class HlsHttp(
        private val client: OkHttpClient,
        private val headers: Map<String, String>,
    ) {
        private val inFlight: MutableSet<Call> = Collections.newSetFromMap(ConcurrentHashMap<Call, Boolean>())
        private val requestCount = AtomicInteger()
        private val byteCount = AtomicLong()
        @Volatile private var closed = false

        val requests: Int get() = requestCount.get()
        val bytes: Long get() = byteCount.get()

        /** Address of the last answer after redirects (playlists resolve relative URLs against it). */
        @Volatile var lastUrl: String? = null
            private set

        /** Up to [limit] bytes of [url] ([range] when given: the server may ignore it, the read still stops). */
        fun get(url: String, range: LongRange?, limit: Int): ByteArray {
            if (closed) throw IOException("closed")
            val builder = Request.Builder().url(url)
            for ((k, v) in headers) {
                if (!k.equals("Range", ignoreCase = true)) builder.header(k, v)
            }
            if (range != null) builder.header("Range", "bytes=${range.first}-${range.last}")
            val call = client.newCall(builder.build())
            inFlight.add(call)
            try {
                requestCount.incrementAndGet()
                call.execute().use { resp ->
                    when (resp.code) {
                        200, 206 -> Unit
                        429, 503 -> throw RateLimitedException(resp.code)
                        else -> throw HttpStatusException(resp.code)
                    }
                    lastUrl = resp.request.url.toString()
                    val source = resp.body.source()
                    val out = ByteArrayOutputStream(minOf(limit, 512 * 1024))
                    val chunk = ByteArray(64 * 1024)
                    // A server that ignored the range answers from byte 0: skip up to where the range starts.
                    var skip = if (resp.code == 200 && range != null) range.first else 0L
                    while (out.size() < limit) {
                        val n = source.read(chunk, 0, chunk.size)
                        if (n < 0) break
                        var from = 0
                        if (skip > 0) {
                            val s = minOf(skip, n.toLong()).toInt()
                            skip -= s
                            from = s
                        }
                        val take = minOf(n - from, limit - out.size())
                        if (take > 0) out.write(chunk, from, take)
                    }
                    byteCount.addAndGet(out.size().toLong())
                    return out.toByteArray()
                }
            } finally {
                inFlight.remove(call)
            }
        }

        fun cancel() {
            for (call in inFlight) call.cancel()
        }

        fun close() {
            closed = true
            cancel()
        }
    }

    /** Opens HLS from a playlist address: the master's lowest-bitrate video variant, or the media playlist itself. */
    object Opener {
        fun open(http: HlsHttp, url: String): HlsThumbSource {
            val t0 = SystemClock.elapsedRealtime()
            val parser = HlsPlaylistParser()
            val firstBytes = http.get(url, null, MAX_PLAYLIST_BYTES)
            val first = parser.parse(Uri.parse(http.lastUrl ?: url), ByteArrayInputStream(firstBytes))
            var label = "media playlist"
            val media = when (first) {
                is HlsMediaPlaylist -> first
                is HlsMultivariantPlaylist -> {
                    val variant = lowestVideoVariant(first)
                        ?: throw UnsupportedMediaException("no video variant")
                    label = "${variant.format.width}x${variant.format.height}@${variant.format.bitrate / 1000}kbps " +
                        "(of ${first.variants.size})"
                    val variantBytes = http.get(variant.url.toString(), null, MAX_PLAYLIST_BYTES)
                    parser.parse(Uri.parse(http.lastUrl ?: variant.url.toString()), ByteArrayInputStream(variantBytes))
                        as? HlsMediaPlaylist ?: throw UnsupportedMediaException("variant is not a media playlist")
                }
                else -> throw UnsupportedMediaException("unknown playlist")
            }
            if (!media.hasEndTag && media.playlistType != HlsMediaPlaylist.PLAYLIST_TYPE_VOD) {
                throw UnsupportedMediaException("live or event playlist")
            }
            if (media.segments.isEmpty()) throw UnsupportedMediaException("empty playlist")
            seekThumbLog.i {
                "HLS opened: $label segments=${media.segments.size} durationS=${media.durationUs / 1_000_000} " +
                    "requests=${http.requests} kb=${http.bytes / 1024} t=${SystemClock.elapsedRealtime() - t0}ms"
            }
            return HlsThumbSource(http, media, label)
        }

        /** Lowest bitrate among H.264 / HEVC variants (those with a resolution when codecs are not declared). */
        private fun lowestVideoVariant(master: HlsMultivariantPlaylist): HlsMultivariantPlaylist.Variant? {
            fun isVideo(v: HlsMultivariantPlaylist.Variant): Boolean {
                val codecs = v.format.codecs?.lowercase()
                if (codecs.isNullOrBlank()) return v.format.width > 0 || master.variants.size == 1
                return codecs.split(',').any { c ->
                    val t = c.trim()
                    t.startsWith("avc") || t.startsWith("hvc") || t.startsWith("hev")
                }
            }
            val candidates = master.variants.filter(::isVideo)
            return candidates.minByOrNull { v -> v.format.bitrate.takeIf { it > 0 } ?: Int.MAX_VALUE }
        }
    }
}
