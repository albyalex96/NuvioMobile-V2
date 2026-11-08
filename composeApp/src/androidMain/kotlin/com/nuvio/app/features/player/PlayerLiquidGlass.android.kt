package com.nuvio.app.features.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.roundToInt

// Behind a blur a 320px-wide, ~15fps copy looks the same as a sharper one and costs far less.
private const val VideoFrameCaptureWidth = 320
private const val VideoFrameCaptureIntervalMs = 66L

/** When the picture stops changing (paused video) the copy slows down to this interval. */
private const val VideoFrameIdleIntervalMs = 300L
private const val VideoFrameBufferCount = 3

/** About half a second of all-black captures means PixelCopy can't read this video (DRM/tunneling/device). */
private const val BlackFrameLimit = 15

/** View tag carried by SurfaceViews that sit over the video (e.g. the libass subtitle layer). */
internal const val PlayerOverlaySurfaceTag = "nuvio:player-overlay-surface"

@Composable
internal actual fun rememberVideoFrameBackdrop(enabled: Boolean): VideoFrameBackdrop? {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val view = LocalView.current
    val backdrop = remember { VideoFrameBackdrop() }
    LaunchedEffect(backdrop, view) {
        val handler = Handler(Looper.getMainLooper())
        val buffers = arrayOfNulls<Bitmap>(VideoFrameBufferCount)
        var nextBuffer = 0
        var blackFrames = 0
        var cachedSurface: SurfaceView? = null
        var lastSignature = 0L
        var unchangedFrames = 0
        val location = IntArray(2)
        snapshotFlow { backdrop.consumers > 0 }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (!active) {
                    backdrop.clearFrame()
                    return@collectLatest
                }
                while (currentCoroutineContext().isActive) {
                    // The view tree is only walked again when the cached video surface goes away.
                    val surfaceView = cachedSurface?.takeIf { it.isAttachedToWindow && it.isUsableVideoSurface() }
                        ?: view.rootView.findVideoSurfaceView().also { cachedSurface = it }
                    var interval = VideoFrameCaptureIntervalMs
                    if (surfaceView != null) {
                        val width = VideoFrameCaptureWidth
                        val height = (width * surfaceView.height / surfaceView.width.toFloat())
                            .roundToInt()
                            .coerceIn(1, width * 4)
                        val bitmap = buffers[nextBuffer]?.takeIf { it.width == width && it.height == height }
                            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                buffers[nextBuffer] = it
                            }
                        val copied = surfaceView.copyFrameInto(bitmap, handler)
                        val signature = if (copied) bitmap.sampleSignature() else 0L
                        if (copied && signature == 0L) {
                            // Never paint a failed (black) capture into the glass; after a run of them fall
                            // back to the clear fallback style instead of showing a dark blob.
                            blackFrames += 1
                            if (blackFrames >= BlackFrameLimit) backdrop.clearFrame()
                        } else if (copied && signature == lastSignature) {
                            // Same picture as before (paused): nothing to repaint, check less often.
                            unchangedFrames += 1
                            if (unchangedFrames >= 3) interval = VideoFrameIdleIntervalMs
                        } else if (copied) {
                            blackFrames = 0
                            unchangedFrames = 0
                            lastSignature = signature
                            surfaceView.getLocationOnScreen(location)
                            backdrop.publishFrame(
                                image = bitmap.asImageBitmap(),
                                boundsOnScreen = Rect(
                                    left = location[0].toFloat(),
                                    top = location[1].toFloat(),
                                    right = (location[0] + surfaceView.width).toFloat(),
                                    bottom = (location[1] + surfaceView.height).toFloat(),
                                ),
                            )
                            nextBuffer = (nextBuffer + 1) % buffers.size
                        }
                    }
                    delay(interval)
                }
            }
    }
    DisposableEffect(backdrop) {
        onDispose { backdrop.clearFrame() }
    }
    return backdrop
}

/**
 * Finds the SurfaceView that actually carries decoded video.
 *
 * ExoPlayer's [PlayerView] is asked directly for its video surface. Otherwise (libmpv, whose view is a
 * SurfaceView itself) the largest visible SurfaceView wins. Overlay surfaces tagged with
 * [PlayerOverlaySurfaceTag] (the full-screen, transparent libass layer) are skipped: when the video is
 * letterboxed they are larger than the video surface and used to be picked, which made the glass empty.
 */
private fun View.findVideoSurfaceView(): SurfaceView? {
    var best: SurfaceView? = null
    var bestArea = 0
    var fromPlayerView: SurfaceView? = null
    fun visit(candidate: View) {
        if (fromPlayerView != null) return
        if (candidate.visibility != View.VISIBLE) return
        if (candidate is PlayerView) {
            val videoSurface = candidate.videoSurfaceView as? SurfaceView
            if (videoSurface != null && videoSurface.isUsableVideoSurface()) {
                fromPlayerView = videoSurface
                return
            }
        }
        if (candidate is SurfaceView) {
            if (candidate.tag == PlayerOverlaySurfaceTag) return
            val area = candidate.width * candidate.height
            if (area > bestArea && candidate.isUsableVideoSurface()) {
                best = candidate
                bestArea = area
            }
        } else if (candidate is ViewGroup) {
            for (index in 0 until candidate.childCount) {
                visit(candidate.getChildAt(index))
            }
        }
    }
    visit(this)
    return fromPlayerView ?: best
}

private fun SurfaceView.isUsableVideoSurface(): Boolean =
    isShown && width > 0 && height > 0 && holder.surface?.isValid == true

/**
 * Cheap fingerprint of a capture from an 8x8 grid of pixels: 0 when every sample is pure black (an
 * empty/protected frame), and the same value for two copies of the same still picture.
 */
private fun Bitmap.sampleSignature(): Long {
    val stepX = (width / 8).coerceAtLeast(1)
    val stepY = (height / 8).coerceAtLeast(1)
    var signature = 0L
    var anyLit = false
    var y = stepY / 2
    while (y < height) {
        var x = stepX / 2
        while (x < width) {
            val rgb = getPixel(x, y) and 0x00FFFFFF
            if (rgb != 0) anyLit = true
            signature = signature * 31 + rgb
            x += stepX
        }
        y += stepY
    }
    return if (anyLit) (signature or 1L) else 0L
}

private suspend fun SurfaceView.copyFrameInto(bitmap: Bitmap, handler: Handler): Boolean {
    if (width <= 0 || height <= 0 || holder.surface?.isValid != true) return false
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                this,
                bitmap,
                { result ->
                    if (continuation.isActive) continuation.resume(result == PixelCopy.SUCCESS)
                },
                handler,
            )
        } catch (_: IllegalArgumentException) {
            if (continuation.isActive) continuation.resume(false)
        }
    }
}
