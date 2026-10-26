package com.nuvio.app.features.player.seekthumb

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.MimeTypes
import com.nuvio.app.features.player.PlayerPlaybackNetworking
import com.nuvio.app.features.player.inferPlaybackMimeType
import com.nuvio.seekthumb.ThumbNative
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.math.abs
import kotlin.math.roundToInt

/** Application context for the preview engine; set once by [com.nuvio.app.NuvioApplication]. */
internal object SeekThumbnailPlatform {
    @Volatile
    var appContext: Context? = null
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}

/** What the device can spare for previews, read once. */
internal object SeekThumbMemory {
    /** Total RAM below which pictures larger than 1080p are not decoded (a 4K software decode takes ~80 MB). */
    private const val LOW_MEMORY_TOTAL_BYTES = 2_600L * 1024 * 1024

    private val info: Pair<Boolean, Int> by lazy {
        val context = SeekThumbnailPlatform.appContext
        val am = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val low = am?.isLowRamDevice == true || mem.totalMem in 1 until LOW_MEMORY_TOTAL_BYTES
        low to (am?.memoryClass ?: 128)
    }

    val lowMemoryDevice: Boolean get() = info.first
    val strongDevice: Boolean get() = !info.first
    val heapClassMb: Int get() = info.second
}

internal actual val seekThumbnailsSupported: Boolean = true

internal actual fun createSeekThumbnailProvider(
    request: SeekThumbnailRequest,
    config: SeekThumbnailConfig,
): SeekThumbnailProvider? {
    val context = SeekThumbnailPlatform.appContext ?: return null
    val uri = Uri.parse(request.url)
    val scheme = uri.scheme?.lowercase()
    val local = scheme == "file" || scheme == "content"
    // The player's own rules (stream type, Content-Type, extension, query and path hints).
    val mimeType = if (local) null else inferPlaybackMimeType(request.url, request.responseHeaders, request.streamType)
    val reason = when {
        !local && scheme != "http" && scheme != "https" -> "address scheme $scheme"
        // A local server is the P2P engine or a proxy: random reads would pull far pieces away from playback.
        !local && isLoopbackHost(uri.host) -> "local stream server"
        mimeType == MimeTypes.APPLICATION_MPD || mimeType == MimeTypes.APPLICATION_SS -> "DASH / Smooth Streaming"
        else -> null
    }
    if (reason != null) {
        seekThumbLog.i { "No seek thumbnails for this stream: $reason" }
        return null
    }
    val hls = mimeType == MimeTypes.APPLICATION_M3U8 || uri.path.orEmpty().lowercase().endsWith(".m3u")
    return AndroidSeekThumbnailProvider(context, request, config, local, hls)
}

private fun isLoopbackHost(host: String?): Boolean = when (host?.lowercase()) {
    "127.0.0.1", "localhost", "::1", "[::1]" -> true
    else -> false
}

/**
 * On-demand previews of one stream. One worker thread: only the last asked position is fetched, one keyframe at a
 * time, decoded in software below playback's priority (the native side runs it in SCHED_IDLE). Pictures stay in a
 * memory LRU sized from the app's heap class; nothing is written to disk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class AndroidSeekThumbnailProvider(
    private val context: Context,
    private val request: SeekThumbnailRequest,
    private val config: SeekThumbnailConfig,
    private val local: Boolean,
    private val hls: Boolean,
) : SeekThumbnailProvider {
    private companion object {
        const val MB = 1024 * 1024
        const val TRIM_AFTER_IDLE_MS = 10_000L
        /** The index is read this long after the session starts, once playback's start-up buffering is done. */
        const val PREPARE_DELAY_MS = 5_000L
        const val MAX_OPEN_FAILURES = 4
        const val MAX_FETCH_FAILURES = 5
        const val RATE_LIMIT_PAUSE_MS = 10_000L
        const val FULL_HD_PIXELS = 1920L * 1088L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val target = MutableStateFlow<Long?>(null)
    private val _frame = MutableStateFlow<SeekThumbnailFrame?>(null)
    override val frame: StateFlow<SeekThumbnailFrame?> = _frame.asStateFlow()

    private val lowMemoryDevice: Boolean = SeekThumbMemory.lowMemoryDevice
    private val cache: ThumbCache
    private val maxWidthPx: Int = (config.size.widthDp * context.resources.displayMetrics.density)
        .roundToInt().coerceIn(96, ThumbGeometry.MAX_WIDTH)

    /** Positions closer than this to a cached picture show it at once while the exact one is fetched. */
    private val nearbyWindowMs = maxOf(20_000L, request.durationMs / 50)

    @Volatile private var closed = false
    @Volatile private var openSource: ThumbSource? = null

    // Worker thread only.
    private var source: ThumbSource? = null
    private var decoder: FfmpegThumbDecoder? = null
    private var disabledReason: String? = null
    private var openFailures = 0
    private var nextOpenAtMs = 0L
    private var fetchFailures = 0
    private var pausedUntilMs = 0L
    private var wifiWaitLogged = false
    private var trimJob: Job? = null
    private var made = 0
    private var fetchMsTotal = 0L
    private var decodeMsTotal = 0L
    private var cacheHits = 0

    private val collectJob: Job

    init {
        val heapClassMb = SeekThumbMemory.heapClassMb
        val cacheBytes = if (lowMemoryDevice) 6 * MB else (heapClassMb * MB / 16).coerceIn(8 * MB, 32 * MB)
        cache = ThumbCache(cacheBytes)
        seekThumbLog.i {
            "Session start: ${if (hls) "HLS" else if (local) "local file" else "single file"} " +
                "size=${config.size} widthPx=$maxWidthPx cacheMb=${cacheBytes / MB} lowMemory=$lowMemoryDevice " +
                "wifiOnly=${config.wifiOnly}"
        }
        collectJob = scope.launch {
            launch {
                // The index is ready before the first scrub, so its first picture does not wait for it.
                delay(PREPARE_DELAY_MS)
                prepare()
            }
            target.collect { positionMs -> if (positionMs != null) serve(positionMs) }
        }
    }

    private fun prepare() {
        if (closed || disabledReason != null || source != null) return
        if (!local && config.wifiOnly && !onWifiOrEthernet()) return
        val t0 = SystemClock.elapsedRealtime()
        if (open() != null) seekThumbLog.d { "Index prepared in advance in ${SystemClock.elapsedRealtime() - t0}ms" }
    }

    override fun request(positionMs: Long) {
        if (closed) return
        cache.nearest(positionMs, nearbyWindowMs)?.let { nearby ->
            if (_frame.value !== nearby) _frame.value = nearby
        }
        target.value = positionMs
    }

    override fun clearFrame() {
        target.value = null
        _frame.value = null
    }

    override fun close() {
        if (closed) return
        closed = true
        target.value = null
        _frame.value = null
        openSource?.cancel()
        collectJob.cancel()
        // Queued behind a fetch or decode under way (one worker thread), then the scope ends.
        scope.launch {
            trimJob?.cancel()
            seekThumbLog.i {
                val avgFetch = if (made > 0) fetchMsTotal / made else 0
                val avgDecode = if (made > 0) decodeMsTotal / made else 0
                "Session end: made=$made cacheHits=$cacheHits avgFetchMs=$avgFetch avgDecodeMs=$avgDecode " +
                    "requests=${requests()} kb=${bytes() / 1024}" + (disabledReason?.let { " disabled=($it)" } ?: "")
            }
            runCatching { decoder?.close() }
            runCatching { source?.close() }
            decoder = null
            source = null
            cache.evictAll()
            scope.cancel()
        }
    }

    private fun requests(): Int = when (val s = source) {
        is ProgressiveThumbSource -> s.requests
        is HlsThumbSource -> s.requests
        else -> 0
    }

    private fun bytes(): Long = when (val s = source) {
        is ProgressiveThumbSource -> s.bytes
        is HlsThumbSource -> s.bytes
        else -> 0L
    }

    private suspend fun serve(positionMs: Long) {
        if (closed || disabledReason != null) return
        if (SystemClock.elapsedRealtime() < pausedUntilMs) return
        if (!local && config.wifiOnly && !onWifiOrEthernet()) {
            if (!wifiWaitLogged) {
                wifiWaitLogged = true
                seekThumbLog.i { "Not on Wi-Fi or Ethernet: previews wait (Wi-Fi only is on)" }
            }
            return
        }
        try {
            val src = source ?: open() ?: return
            val key = src.keyFor(positionMs)
            if (key < 0) return
            cache.get(key)?.let { cached ->
                cacheHits++
                publish(cached)
                return
            }
            // The scrub moved on to another picture while this one waited: serve that one instead.
            val latest = target.value
            if (latest == null) {
                seekThumbLog.d { "Scrub ended before the picture at ${key / 1000}s was fetched" }
                return
            }
            if (latest != positionMs && src.keyFor(latest) != key) return
            val t0 = SystemClock.elapsedRealtime()
            val keyframe = src.fetch(key) ?: run {
                seekThumbLog.d { "No keyframe at ${key / 1000}s" }
                return
            }
            val t1 = SystemClock.elapsedRealtime()
            checkAffordable(src)
            val decoder = decoder ?: FfmpegThumbDecoder().also { decoder = it }
            val bitmap = decoder.picture(keyframe, maxWidthPx) ?: run {
                seekThumbLog.w { "Keyframe at ${keyframe.positionMs / 1000}s could not be decoded" }
                return
            }
            val t2 = SystemClock.elapsedRealtime()
            val thumb = SeekThumbnailFrame(bitmap.asImageBitmap(), keyframe.positionMs)
            cache.put(key, thumb, bitmap.byteCount)
            made++
            fetchFailures = 0
            fetchMsTotal += t1 - t0
            decodeMsTotal += t2 - t1
            seekThumbLog.d {
                "Thumb at ${keyframe.positionMs / 1000}s fetchMs=${t1 - t0} decodeMs=${t2 - t1} " +
                    "${bitmap.width}x${bitmap.height} kbTotal=${bytes() / 1024}"
            }
            publish(thumb)
            scheduleTrim()
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnsupportedMediaException) {
            disable(e.message ?: "unsupported")
        } catch (e: RateLimitedException) {
            pausedUntilMs = SystemClock.elapsedRealtime() + RATE_LIMIT_PAUSE_MS
            seekThumbLog.w { "Server rate limited previews (${e.status}): paused ${RATE_LIMIT_PAUSE_MS / 1000}s" }
        } catch (e: IOException) {
            if (closed) return
            fetchFailures++
            seekThumbLog.w(e) { "Preview read failed ($fetchFailures/$MAX_FETCH_FAILURES)" }
            if (fetchFailures >= MAX_FETCH_FAILURES) disable("read failures")
        } catch (e: OutOfMemoryError) {
            cache.evictAll()
            disable("out of memory")
        }
    }

    /** Shows [thumb] when it is the closest picture to the position asked last. */
    private fun publish(thumb: SeekThumbnailFrame) {
        val latest = target.value ?: return
        val shown = _frame.value
        if (shown == null || abs(thumb.positionMs - latest) <= abs(shown.positionMs - latest)) {
            _frame.value = thumb
        }
    }

    private fun open(): ThumbSource? {
        val now = SystemClock.elapsedRealtime()
        if (now < nextOpenAtMs) return null
        if (!SeekThumbNativeLoader.load()) {
            disable("native decoder ${SeekThumbNativeLoader.state()}")
            return null
        }
        val client = PlayerPlaybackNetworking.seekThumbnailHttpClient()
        val headers = PlayerPlaybackNetworking.seekThumbnailRequestHeaders(request.headers)
        return try {
            val opened: ThumbSource = if (hls) {
                HlsThumbSource.Opener.open(HlsThumbSource.HlsHttp(client, headers), request.url)
            } else {
                val reader: CountingRangeReader = if (local) {
                    FileRangeReader.open(context, request.url) ?: throw UnsupportedMediaException("local file not readable")
                } else {
                    HttpRangeReader(request.url, headers, client)
                }
                val head = try {
                    reader.read(0, MkvIndexReader.HEAD_BYTES)
                } catch (e: UnsupportedMediaException) {
                    reader.close()
                    // A playlist server answers 200 to a Range request: an address without a telling name may
                    // still be HLS, which its first line says.
                    val http = HlsThumbSource.HlsHttp(client, headers)
                    if (local || !http.get(request.url, null, 1024).startsWith("#EXTM3U")) throw e
                    seekThumbLog.i { "Stream recognised as HLS from its content" }
                    null
                } catch (e: Throwable) {
                    reader.close()
                    throw e
                }
                if (head == null || head.startsWith("#EXTM3U")) {
                    reader.close()
                    HlsThumbSource.Opener.open(HlsThumbSource.HlsHttp(client, headers), request.url)
                } else {
                    try {
                        ProgressiveThumbSource.open(reader, head)
                    } catch (e: Throwable) {
                        reader.close()
                        throw e
                    }
                }
            }
            openFailures = 0
            source = opened
            openSource = opened
            if (closed) opened.cancel()
            seekThumbLog.i { "Source ready: ${opened.description}" }
            checkAffordable(opened)
            opened
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnsupportedMediaException) {
            disable(e.message ?: "unsupported")
            null
        } catch (e: RateLimitedException) {
            nextOpenAtMs = now + RATE_LIMIT_PAUSE_MS
            seekThumbLog.w { "Server rate limited the index read (${e.status})" }
            null
        } catch (e: IOException) {
            if (closed) return null
            openFailures++
            nextOpenAtMs = now + (2_000L shl (openFailures - 1).coerceAtMost(4))
            seekThumbLog.w(e) { "Index read failed ($openFailures/$MAX_OPEN_FAILURES)" }
            if (openFailures >= MAX_OPEN_FAILURES) disable("index read failures")
            null
        }
    }

    /** A picture above 1080p is not decoded on a low-memory device. */
    private fun checkAffordable(src: ThumbSource) {
        val pixels = src.videoWidth.toLong() * src.videoHeight
        if (lowMemoryDevice && pixels > FULL_HD_PIXELS) {
            throw UnsupportedMediaException("${src.videoWidth}x${src.videoHeight} on a low-memory device")
        }
    }

    private fun disable(reason: String) {
        if (disabledReason != null) return
        disabledReason = reason
        seekThumbLog.i { "No seek thumbnails for this stream: $reason" }
        runCatching { decoder?.close() }
        decoder = null
    }

    /** The decoder's working set is dropped after a pause in scrubbing; the context stays for the next scrub. */
    private fun scheduleTrim() {
        trimJob?.cancel()
        trimJob = scope.launch {
            delay(TRIM_AFTER_IDLE_MS)
            decoder?.trim()
            runCatching { ThumbNative.nativePurge() }
        }
    }

    private fun onWifiOrEthernet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}

internal fun ByteArray.startsWith(text: String): Boolean {
    if (size < text.length) return false
    for (i in text.indices) if (this[i] != text[i].code.toByte()) return false
    return true
}

/** Memory LRU of pictures by key (the picture's position in ms), with a sorted view for the nearest lookup. */
private class ThumbCache(maxBytes: Int) {
    private val sorted = ConcurrentSkipListMap<Long, SeekThumbnailFrame>()
    private val lru = object : LruCache<Long, Entry>(maxBytes) {
        override fun sizeOf(key: Long, value: Entry): Int = value.bytes

        override fun entryRemoved(evicted: Boolean, key: Long, oldValue: Entry, newValue: Entry?) {
            if (newValue == null) sorted.remove(key, oldValue.frame)
        }
    }

    private class Entry(val frame: SeekThumbnailFrame, val bytes: Int)

    fun get(key: Long): SeekThumbnailFrame? = lru.get(key)?.frame

    fun put(key: Long, frame: SeekThumbnailFrame, bytes: Int) {
        lru.put(key, Entry(frame, bytes.coerceAtLeast(1)))
        sorted[key] = frame
    }

    fun nearest(positionMs: Long, windowMs: Long): SeekThumbnailFrame? {
        val below = sorted.floorEntry(positionMs)
        val above = sorted.ceilingEntry(positionMs)
        val best = when {
            below == null -> above
            above == null -> below
            positionMs - below.key <= above.key - positionMs -> below
            else -> above
        } ?: return null
        return best.value.takeIf { abs(best.key - positionMs) <= windowMs }
    }

    fun evictAll() {
        lru.evictAll()
        sorted.clear()
    }
}
