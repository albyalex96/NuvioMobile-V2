package com.nuvio.app.features.player.seekthumb

import android.graphics.Bitmap
import android.os.SystemClock
import com.nuvio.seekthumb.ThumbNative
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Loads libnuvioseekthumb and MPV's FFmpeg (ported from Nuvio Arc, GPL-3.0). A failed load is tried again by a
 * later request, after [NativeLoadBackoff]'s wait. The first load takes 150-440 ms: never on the main thread.
 */
internal object SeekThumbNativeLoader {
    private val retry = NativeLoadBackoff()
    @Volatile private var libraryLoaded = false
    @Volatile private var ready = false
    @Volatile private var backendName: String? = null
    @Volatile private var lastError: String? = null

    /** True once the library and MPV's FFmpeg are loaded. Never loads anything itself, see [load]. */
    val available: Boolean get() = ready

    fun load(): Boolean {
        if (ready) return true
        synchronized(this) {
            if (ready) return true
            val now = SystemClock.elapsedRealtime()
            if (!retry.mayTry(now)) return false
            try {
                if (!libraryLoaded) {
                    ThumbNative.loadLibrary()
                    libraryLoaded = true
                }
                if (ThumbNative.nativeInit()) {
                    val backend = ThumbNative.nativeBackend()
                    backendName = backend.substringBefore(' ')
                    ready = true
                    seekThumbLog.i {
                        "Decoder backend: $backend" + if (retry.failures > 0) " (after ${retry.failures} failed tries)" else ""
                    }
                    return true
                }
                lastError = runCatching { ThumbNative.nativeLastError() }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: "no FFmpeg backend"
            } catch (t: Throwable) {
                lastError = "${t.javaClass.simpleName}: ${t.message}"
            }
            retry.failed(now)
            seekThumbLog.w {
                "Native decoder unavailable (try ${retry.failures}): $lastError; " +
                    "tried again in ${retry.waitMs(now) / 1000} s at the earliest"
            }
            return false
        }
    }

    /** For the log: the backend in use, or why none is. */
    fun state(): String = when {
        ready -> "ok ${backendName.orEmpty()}".trim()
        lastError == null -> "not loaded yet"
        else -> "unavailable after ${retry.failures} tries ($lastError)"
    }
}

/** When a failed native load may be tried again: [FIRST_WAIT_MS] after the first failure, doubling, at most [MAX_WAIT_MS]. */
internal class NativeLoadBackoff {
    companion object {
        const val FIRST_WAIT_MS = 60_000L
        const val MAX_WAIT_MS = 15 * 60_000L
    }

    @Volatile var failures = 0
        private set
    private var nextTryAtMs = Long.MIN_VALUE

    fun mayTry(nowMs: Long): Boolean = nowMs >= nextTryAtMs

    fun failed(nowMs: Long) {
        failures++
        nextTryAtMs = nowMs + (FIRST_WAIT_MS shl (failures - 1).coerceAtMost(10)).coerceAtMost(MAX_WAIT_MS)
    }

    fun waitMs(nowMs: Long): Long = if (failures == 0) 0L else (nextTryAtMs - nowMs).coerceAtLeast(0L)
}

internal class DecodedFrame(
    val width: Int,
    val height: Int,
    val sarNum: Int,
    val sarDen: Int,
    val colour: FrameColour,
    /** Strips the picture was decoded in, 0 = whole. */
    val strips: Int = 0,
)

internal object ThumbGeometry {
    const val MAX_WIDTH = 480

    /** Output size from the display aspect (container first, then the bitstream's SAR). Never upscaled, even height. */
    fun outputSize(
        frameW: Int,
        frameH: Int,
        sarNum: Int,
        sarDen: Int,
        containerAspect: Double,
        maxWidth: Int = MAX_WIDTH,
    ): IntArray {
        val dar = when {
            containerAspect > 0.0 -> containerAspect
            sarNum > 0 && sarDen > 0 -> frameW.toDouble() * sarNum / (frameH.toDouble() * sarDen)
            else -> frameW.toDouble() / frameH
        }.takeIf { it.isFinite() && it > 0.1 && it < 10.0 } ?: (frameW.toDouble() / frameH)
        val displayW = (frameH * dar).roundToInt().coerceAtLeast(2)
        val w = minOf(maxWidth, displayW) and 1.inv()
        val h = ((w / dar).roundToInt() and 1.inv()).coerceAtLeast(2)
        return intArrayOf(w.coerceAtLeast(2), h)
    }
}

/**
 * Software keyframe decoder over the in-APK FFmpeg. One context, reused across a title's keyframes.
 * Not thread-safe: each decode lane owns one.
 */
internal class FfmpegThumbDecoder : Closeable {
    private var handle = 0L
    private var handleTrack: VideoTrack? = null
    private var handleThreads = 0
    private val info = IntArray(ThumbNative.INFO_COUNT)
    private var decodedSinceTrim = false
    @Volatile private var directDecode = true

    /** The working set is allocated and already missing from MemAvailable, so the governor must not charge it again. */
    val warm: Boolean get() = handle != 0L && decodedSinceTrim

    /** [annexB]: one keyframe access unit, Annex-B for H.264/HEVC, the raw sample otherwise. */
    fun decode(annexB: ByteArray, track: VideoTrack, threads: Int): DecodedFrame? {
        if (!open(track, threads)) return null
        return frame(ThumbNative.nativeDecode(handle, annexB, annexB.size, info))
    }

    /** As above, from a buffer; a direct one reaches FFmpeg without a copy on the Java heap. */
    fun decode(au: ThumbAu, track: VideoTrack, threads: Int): DecodedFrame? {
        if (!open(track, threads)) return null
        val d = au.data
        val r = when {
            d.isDirect && directDecode -> try {
                ThumbNative.nativeDecodeDirect(handle, d, au.length, info)
            } catch (e: UnsatisfiedLinkError) {
                directDecode = false
                ThumbNative.nativeDecode(handle, au.toByteArray(), au.length, info)
            }
            d.hasArray() && d.arrayOffset() == 0 -> ThumbNative.nativeDecode(handle, d.array(), au.length, info)
            else -> ThumbNative.nativeDecode(handle, au.toByteArray(), au.length, info)
        }
        return frame(r)
    }

    private fun open(track: VideoTrack, threads: Int): Boolean {
        if (!SeekThumbNativeLoader.available) return false
        if (handle == 0L || handleTrack !== track || handleThreads != threads) {
            close()
            handle = ThumbNative.nativeOpen(
                track.codec.ffmpegName, track.extradata.takeIf { it.isNotEmpty() }, track.width, track.height, threads,
            )
            if (handle == 0L) {
                seekThumbLog.w { "No ${track.codec.ffmpegName} decoder in this FFmpeg" }
                return false
            }
            handleTrack = track
            handleThreads = threads
        }
        return true
    }

    private fun frame(r: Int): DecodedFrame? {
        if (r < 0) {
            seekThumbLog.w { "Decode failed: $r" }
            return null
        }
        decodedSinceTrim = true
        return DecodedFrame(
            width = info[ThumbNative.INFO_WIDTH],
            height = info[ThumbNative.INFO_HEIGHT],
            sarNum = info[ThumbNative.INFO_SAR_NUM],
            sarDen = info[ThumbNative.INFO_SAR_DEN],
            colour = FrameColour(
                bitDepth = info[ThumbNative.INFO_BIT_DEPTH],
                transfer = info[ThumbNative.INFO_TRC],
                primaries = info[ThumbNative.INFO_PRIMARIES],
                matrix = info[ThumbNative.INFO_MATRIX],
                range = info[ThumbNative.INFO_RANGE],
                maxCll = info[ThumbNative.INFO_MAX_CLL],
                masteringMaxNits = info[ThumbNative.INFO_MASTERING_MAX_NITS],
                doviIpt = info[ThumbNative.INFO_DOVI_IPT] != 0,
            ),
            strips = info[ThumbNative.INFO_STRIPS],
        )
    }

    /**
     * Renders the last decoded frame into a new bitmap of [outW] x [outH]: RGB_565 halves the memory a kept picture
     * takes. Every other source row is skipped only for 4K-sized sources.
     */
    fun render(frame: DecodedFrame, decision: ColourDecision, outW: Int, outH: Int, rgb565: Boolean = true): Bitmap? {
        if (handle == 0L) return null
        val bmp = Bitmap.createBitmap(outW, outH, if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
        val rowSkip = frame.height >= 6 * outH
        val r = ThumbNative.nativeRender(
            handle, bmp, decision.transfer.nativeCode, decision.bt2020, decision.fullRange,
            decision.peakNits, rowSkip,
        )
        if (r < 0) {
            seekThumbLog.w { "Render failed: $r" }
            bmp.recycle()
            return null
        }
        return bmp
    }

    fun trim() {
        if (handle != 0L) ThumbNative.nativeTrim(handle)
        decodedSinceTrim = false
    }

    override fun close() {
        decodedSinceTrim = false
        if (handle != 0L) {
            ThumbNative.nativeRelease(handle)
            handle = 0L
            handleTrack = null
        }
    }
}
