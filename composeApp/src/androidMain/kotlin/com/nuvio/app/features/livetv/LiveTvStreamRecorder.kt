package com.nuvio.app.features.livetv

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.nuvioSharedOkHttpClient
import com.nuvio.app.features.downloads.hls.HlsKey
import com.nuvio.app.features.downloads.hls.HlsKeyMethod
import com.nuvio.app.features.downloads.hls.HlsPlaylistParser
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response

/**
 * Copies a live stream into [output] until [endMillis]: a continuous MPEG-TS stream byte by byte,
 * or an HLS playlist segment by segment (AES-128 segments are decrypted). Dropped connections are
 * reopened until the end time, so a short outage leaves only a gap in the file.
 */
internal class LiveTvStreamRecorder(
    private val url: String,
    private val headers: Map<String, String>,
    private val endMillis: Long,
    private val output: OutputStream,
    private val onProgress: (bytes: Long) -> Unit,
) {
    private val log = Logger.withTag("LiveTvRecording")
    private val client = nuvioSharedOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    var bytesWritten: Long = 0L
        private set

    @Volatile
    private var currentCall: Call? = null

    @Volatile
    private var aborted = false

    /** Stops at once: closes the open connection (a blocked read does not see a cancellation). */
    fun abort() {
        aborted = true
        currentCall?.cancel()
    }

    private fun execute(target: String): Response {
        if (aborted) throw CancellationException("Recording stopped")
        val call = client.newCall(request(target))
        currentCall = call
        return call.execute()
    }
    private var lastProgressAt = 0L

    /** Records until the end time (or until cancelled). Throws only if nothing at all could be recorded. */
    suspend fun run() {
        var lastError: Throwable? = null
        var attempts = 0
        while (System.currentTimeMillis() < endMillis && !aborted) {
            currentCoroutineContext().ensureActive()
            try {
                if (isHls()) recordHls() else recordContinuous()
                // A continuous stream that ends on its own is reopened (providers cut long connections).
            } catch (error: CancellationException) {
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
        if (bytesWritten == 0L) throw IOException(lastError?.message ?: "No data received", lastError)
    }

    private fun request(target: String): Request = Request.Builder().url(target).apply {
        headers.forEach { (name, value) -> header(name, value) }
        if (headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) header("User-Agent", "Nuvio/1.0")
    }.build()

    private var hlsDetected: Boolean? = null

    private fun isHls(): Boolean {
        hlsDetected?.let { return it }
        val lower = url.substringBefore('?').lowercase()
        val detected = lower.endsWith(".m3u8") || lower.endsWith(".m3u")
        hlsDetected = detected
        return detected
    }

    private fun write(bytes: ByteArray, length: Int = bytes.size) {
        output.write(bytes, 0, length)
        bytesWritten += length
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
                if (read > 0) write(buffer, read)
            }
        }
    }

    // ------------------------------------------------------------------------------- HLS

    private suspend fun recordHls() {
        var playlistUrl = url
        val first = fetchText(playlistUrl)
        if (HlsPlaylistParser.isMultivariant(first.second)) {
            val master = HlsPlaylistParser.parseMultivariant(first.second, first.first)
            val best = master.variants.maxByOrNull { it.bandwidth ?: 0L }
                ?: throw IOException("Empty HLS master playlist")
            playlistUrl = best.uri
            log.i { "HLS recording: variant ${best.height ?: "?"}p, ${best.bandwidth ?: 0} bps" }
        }
        val written = LinkedHashSet<Long>()
        val keys = HashMap<String, ByteArray>()
        var initWritten: String? = null
        while (System.currentTimeMillis() < endMillis) {
            currentCoroutineContext().ensureActive()
            val (baseUrl, text) = fetchText(playlistUrl)
            val media = HlsPlaylistParser.parseMedia(text, baseUrl)
            for (segment in media.segments) {
                if (System.currentTimeMillis() >= endMillis) break
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
                write(data)
            }
            // Keeps the memory of seen segments small on very long recordings.
            while (written.size > 2_000) written.remove(written.first())
            if (media.isComplete) {
                if (media.hasEndList) throw IOException("HLS stream ended")
            }
            val waitMillis = ((media.targetDurationSeconds ?: 6.0) * 500).toLong().coerceIn(1_000L, 6_000L)
            delay(waitMillis)
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
