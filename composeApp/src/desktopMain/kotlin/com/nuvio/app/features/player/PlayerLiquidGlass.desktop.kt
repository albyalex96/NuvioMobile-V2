package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import co.touchlab.kermit.Logger
import com.nuvio.app.desktop.mpv.MpvPlayer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt

// Behind a blur a small, ~15fps copy of the video looks the same as the full frame and costs far less.
private const val VideoFrameCaptureWidth = 320
private const val VideoFrameCaptureIntervalMs = 66L

/** When mpv stops producing frames (paused video) the copy slows down to this interval. */
private const val VideoFrameIdleIntervalMs = 300L

private val log = Logger.withTag("LiquidGlass")

/** The mpv player on screen and where its video is drawn, read by the player glass. */
internal object DesktopVideoFrameSource {
    @Volatile
    var player: MpvPlayer? = null

    @Volatile
    var bounds: Rect = Rect.Zero
        private set

    /** Records the video area in the same space the glass uses (screen, else window). */
    fun updateBounds(coordinates: LayoutCoordinates) {
        val onScreen = coordinates.positionOnScreen()
        val origin: Offset = if (onScreen.isSpecified) onScreen else coordinates.positionInWindow()
        bounds = Rect(
            left = origin.x,
            top = origin.y,
            right = origin.x + coordinates.size.width,
            bottom = origin.y + coordinates.size.height,
        )
    }
}

@Composable
internal actual fun rememberVideoFrameBackdrop(enabled: Boolean): VideoFrameBackdrop? {
    if (!enabled) return null
    val backdrop = remember { VideoFrameBackdrop() }
    LaunchedEffect(backdrop) {
        var surface: Surface? = null
        var lastFrameNumber = -1
        snapshotFlow { backdrop.consumers > 0 }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (!active) {
                    backdrop.clearFrame()
                    return@collectLatest
                }
                log.d { "Desktop player glass: video copy started" }
                while (currentCoroutineContext().isActive) {
                    var interval = VideoFrameCaptureIntervalMs
                    val player = DesktopVideoFrameSource.player
                    val bounds = DesktopVideoFrameSource.bounds
                    val frame = player?.currentFrame()
                    val frameNumber = player?.frameNumber() ?: -1
                    if (frame == null || bounds.isEmpty || frame.width <= 0 || frame.height <= 0) {
                        backdrop.clearFrame()
                    } else if (frameNumber == lastFrameNumber) {
                        // Same picture as before (paused): nothing to repaint, check less often.
                        interval = VideoFrameIdleIntervalMs
                    } else {
                        lastFrameNumber = frameNumber
                        val width = VideoFrameCaptureWidth
                        val height = (width * frame.height / frame.width.toFloat()).roundToInt().coerceIn(1, width * 4)
                        val target = surface?.takeIf { it.width == width && it.height == height }
                            ?: Surface.makeRasterN32Premul(width, height).also {
                                surface?.close()
                                surface = it
                            }
                        runCatching {
                            Image.makeFromBitmap(frame).use { image ->
                                target.canvas.drawImageRect(
                                    image,
                                    org.jetbrains.skia.Rect.makeWH(width.toFloat(), height.toFloat()),
                                )
                            }
                            target.makeImageSnapshot().toComposeImageBitmap()
                        }.onSuccess { copy ->
                            backdrop.publishFrame(copy, bounds)
                        }.onFailure { error ->
                            log.w(error) { "Desktop player glass: video copy failed" }
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
