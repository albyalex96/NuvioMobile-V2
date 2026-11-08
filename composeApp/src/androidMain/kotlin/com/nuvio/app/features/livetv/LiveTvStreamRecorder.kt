package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.nuvioSharedOkHttpClient
import com.nuvio.app.features.downloads.hls.HlsKey
import com.nuvio.app.features.downloads.hls.HlsKeyMethod
import com.nuvio.app.features.downloads.hls.HlsMediaPlaylist
import com.nuvio.app.features.downloads.hls.HlsPlaylistParser
import com.nuvio.app.features.downloads.hls.HlsRenditionType
import com.nuvio.app.features.downloads.hls.HlsSegmentFormat
import com.nuvio.app.features.downloads.hls.sniffSegmentFormat
import com.nuvio.app.features.downloads.hls.stripId3
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response

/**
 * Copies a live stream until [endMillis]: a continuous MPEG-TS stream byte by byte into
 * [output], or an HLS playlist segment by segment (AES-128 segments are decrypted). When the
 * HLS variant carries its audio in a separate rendition (most broadcasters do), that rendition is
 * recorded too, into [audioOutput], and the two are merged by the conversion at the end.
 * Dropped connections are reopened until the end time, so a short outage leaves only a gap.
 */
internal class LiveTvStreamRecorder(
    private val url: String,
    private val headers: Map<String, String>,
    private val endMillis: Long,
    private val output: OutputStream,
    /** Opened the first time a separate audio rendition is found. */
    private val audioOutput: () -> OutputStream,
    private val onProgress: (bytes: Long) -> Unit,
) {
    private val log = Logger.withTag("LiveTvRecording")
    private val client = nuvioSharedOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Bytes of the main (video) file plus the separate audio. */
    val bytesWritten: Long get() = video.bytes + (audio?.bytes ?: 0L)

    /** Container of the main file: MPEG-TS, or fragmented MP4 for fMP4 HLS. */
    val videoFormat: HlsSegmentFormat get() = video.format ?: HlsSegmentFormat.Ts

    /** The separate audio rendition, when there was one and something was recorded. */
    val separateAudio: SeparateAudio?
        get() = audio?.takeIf { it.bytes > 0 }?.let { SeparateAudio(it.format ?: HlsSegmentFormat.Ts, it.packedPts) }

    class SeparateAudio(val format: HlsSegmentFormat, val packedPts: Long?)

    @Volatile
    private var currentCall: Call? = null

    @Volatile
    private var aborted = false

    private var lastProgressAt = 0L
    private val video = Track("video") { output }
    private var audio: Track? = null
    private var hlsDetected: Boolean? = null

    /** Stops at once: closes the open connection (a blocked read does not see a cancellation). */
    fun abort() {
        aborted = true
        currentCall?.cancel()
    }

    /** Records until the end time (or until aborted). Throws only if nothing at all could be recorded. */
    suspend fun run() {
        var lastError: Throwable? = null
        var attempts = 0
        while (System.currentTimeMillis() < endMillis && !aborted) {
            currentCoroutineContext().ensureActive()
            try {
                if (isHls()) recordHls() else recordContinuous()
                // A continuous stream that ends on its own is reopened (providers cut long connections).
            } catch (error: CancellationException) {
                if (aborted) break
                throw error
            } catch (error: Throwable) {
                if (aborted) break
                lastError = error
                attempts++
                log.w { "Recording stream interrupted (attempt $attempts): ${error.message}" }
            }
            if (System.currentTimeMillis() >= endMillis || aborted) break
            delay(if (attempts > 5) 10_000L else 3_000L)
        }
        output.flush()
        audio?.flush()
        if (bytesWritten == 0L) throw IOException(lastError?.message ?: "No data received", lastError)
    }

    private fun request(target: String): Request = Request.Builder().url(target).apply {
        headers.forEach { (name, value) -> header(name, value) }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) header("User-Agent", "Nuvio/1.0")
    }.build()

    private fun execute(target: String): Response {
        if (aborted) throw CancellationException("Recording stopped")
        val call = client.newCall(request(target))
        currentCall = call
        return call.execute()
    }

    private fun isHls(): Boolean {
        hlsDetected?.let { return it }
        val lower = url.substringBefore('?').lowercase()
        val detected = lower.endsWith(".m3u8") || lower.endsWith(".m3u")
        hlsDetected = detected
        return detected
    }

    private fun reportProgress() {
        val now = System.currentTimeMillis()
        if (now - lastProgressAt > 3_000L) {
            lastProgressAt = now
            onProgress(bytesWritten)
        }
    }

    // ------------------------------------------------------------------------------- MPEG-TS

    private suspend fun recordContinuous() {
        execute(url).use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            if ("mpegurl" in contentType) {
                // An extensionless HLS URL: switch to the playlist reader.
                hlsDetected = true
                response.close()
                recordHls()
                return
            }
            val source = response.body.byteStream()
            val buffer = ByteArray(64 * 1024)
            while (System.currentTimeMillis() < endMillis && !aborted) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer)
                if (read < 0) throw IOException("Stream ended")
                if (read > 0) {
                    video.write(buffer, read)
                    reportProgress()
                }
            }
        }
    }

    // ------------------------------------------------------------------------------- HLS

    /** One HLS media playlist being followed, and the file its segments go to. */
    private inner class Track(val label: String, private val openOutput: () -> OutputStream) {
        var playlistUrl: String = ""
        var bytes = 0L
        var format: HlsSegmentFormat? = null
        /** Packed audio: MPEG-2 timestamp of the first segment (from its ID3 tag), to sync with the video. */
        var packedPts: Long? = null
        private var stream: OutputStream? = null
        private val written = LinkedHashSet<Long>()
        private val keys = HashMap<String, ByteArray>()
        private var initWritten: String? = null

        fun write(data: ByteArray, length: Int = data.size) {
            val target = stream ?: openOutput().also { stream = it }
            target.write(data, 0, length)
            bytes += length
        }

        fun flush() {
            stream?.flush()
        }

        /** Reads the playlist once and writes its new segments. */
        suspend fun poll(): HlsMediaPlaylist {
            val (baseUrl, text) = fetchText(playlistUrl)
            val media = HlsPlaylistParser.parseMedia(text, baseUrl)
            for (segment in media.segments) {
                if (System.currentTimeMillis() >= endMillis || aborted) break
                currentCoroutineContext().ensureActive()
                if (!written.add(segment.mediaSequence) || segment.isGap) continue
                segment.initSegment?.let { init ->
                    if (init.uri != initWritten) {
                        write(fetchBytes(init.uri))
                        initWritten = init.uri
                    }
                }
                var data = fetchBytes(segment.uri)
                val key = segment.key
                if (key != null && key.method != HlsKeyMethod.None) {
                    if (!key.isSupported) throw IOException("Encrypted stream (DRM): it cannot be recorded")
                    data = decrypt(data, key, segment.mediaSequence, keys)
                }
                val kind = format ?: sniffSegmentFormat(data, segment.initSegment != null).also {
                    format = it
                    log.i { "HLS $label segments: $it" }
                }
                if (kind == HlsSegmentFormat.PackedAac || kind == HlsSegmentFormat.PackedAc3 || kind == HlsSegmentFormat.PackedMp3) {
                    val (pts, payload) = stripId3(data)
                    if (packedPts == null && bytes == 0L) packedPts = pts
                    data = payload
                }
                write(data)
                reportProgress()
            }
            // Keeps the memory of seen segments small on very long recordings.
            while (written.size > 2_000) written.remove(written.first())
            return media
        }
    }

    private suspend fun recordHls() {
        if (video.playlistUrl.isEmpty()) resolveHlsPlaylists()
        while (System.currentTimeMillis() < endMillis && !aborted) {
            currentCoroutineContext().ensureActive()
            val media = video.poll()
            audio?.poll()
            if (media.hasEndList) throw IOException("HLS stream ended")
            val waitMillis = ((media.targetDurationSeconds ?: 6.0) * 500).toLong().coerceIn(1_000L, 6_000L)
            delay(waitMillis)
        }
    }

    /** Picks the best variant and, when its audio is a separate rendition, that rendition too. */
    private fun resolveHlsPlaylists() {
        val (baseUrl, text) = fetchText(url)
        if (!HlsPlaylistParser.isMultivariant(text)) {
            video.playlistUrl = url
            return
        }
        val master = HlsPlaylistParser.parseMultivariant(text, baseUrl)
        val best = master.variants.maxByOrNull { it.bandwidth ?: 0L } ?: throw IOException("Empty HLS master playlist")
        video.playlistUrl = best.uri
        val renditions = master.renditions.filter {
            it.type == HlsRenditionType.Audio && it.uri != null &&
                (best.audioGroupId == null || it.groupId == best.audioGroupId)
        }
        val chosen = renditions.firstOrNull { it.isDefault } ?: renditions.firstOrNull { it.autoSelect } ?: renditions.firstOrNull()
        if (chosen != null) {
            audio = Track("audio", audioOutput).also { it.playlistUrl = chosen.uri!! }
        }
        log.i {
            "HLS recording: variant ${best.height ?: "?"}p, ${best.bandwidth ?: 0} bps" +
                (chosen?.let { ", separate audio '${it.name ?: it.language ?: it.groupId}'" } ?: ", audio in the video stream")
        }
    }

    private fun fetchText(target: String): Pair<String, String> =
        execute(target).use { response ->
            if (!response.isSuccessful) throw IOException("Playlist HTTP ${response.code}")
            // Redirects: segment URLs are relative to the final address.
            response.request.url.toString() to response.body.string()
        }

    private fun fetchBytes(target: String): ByteArray =
        execute(target).use { response ->
            if (!response.isSuccessful) throw IOException("Segment HTTP ${response.code}")
            response.body.bytes()
        }

    private fun decrypt(data: ByteArray, key: HlsKey, sequence: Long, cache: MutableMap<String, ByteArray>): ByteArray {
        val keyUri = key.uri ?: throw IOException("HLS key without address")
        val keyBytes = cache.getOrPut(keyUri) { fetchBytes(keyUri) }
        val iv = key.iv ?: ByteArray(16).also { bytes ->
            var value = sequence
            for (index in 15 downTo 8) {
                bytes[index] = (value and 0xFF).toByte()
                value = value shr 8
            }
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }
}
