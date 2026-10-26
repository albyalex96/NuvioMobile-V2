package com.nuvio.app.features.player.seekthumb

import androidx.compose.ui.graphics.ImageBitmap
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.StateFlow

internal const val SEEK_THUMBNAILS_LOG_TAG = "SeekThumbnails"

internal val seekThumbLog = Logger.withTag(SEEK_THUMBNAILS_LOG_TAG)

/** Width of the preview above the seek bar; the picture is decoded at about this size, never larger. */
enum class SeekThumbnailSize(val widthDp: Int) {
    Small(144),
    Medium(192),
    Large(256),
}

/** A decoded preview. [positionMs] is where its keyframe really is, which may differ from the asked position. */
class SeekThumbnailFrame(
    val image: ImageBitmap,
    val positionMs: Long,
)

/** The stream a provider makes previews of: the same address and headers the player uses. */
data class SeekThumbnailRequest(
    val url: String,
    val headers: Map<String, String>,
    val streamType: String?,
    val durationMs: Long,
    /** The stream's known response headers (Content-Type tells HLS from a single file). */
    val responseHeaders: Map<String, String> = emptyMap(),
)

data class SeekThumbnailConfig(
    val size: SeekThumbnailSize,
    /** Remote streams get previews only on Wi-Fi or Ethernet; local files always do. */
    val wifiOnly: Boolean,
)

/**
 * Previews of one stream. [request] is cheap and may be called on every scrub frame: only the last asked position is
 * worked on, one at a time, on a background thread. [frame] holds the picture closest to the last asked position.
 */
interface SeekThumbnailProvider {
    val frame: StateFlow<SeekThumbnailFrame?>

    fun request(positionMs: Long)

    /** The scrub ended: the shown picture is let go, the decoded ones stay cached for the next scrub. */
    fun clearFrame()

    fun close()
}

/** False where the platform has no preview decoder (the setting is hidden there). */
internal expect val seekThumbnailsSupported: Boolean

/** Null when this stream gets no previews (live, unsupported address, platform without a decoder). */
internal expect fun createSeekThumbnailProvider(
    request: SeekThumbnailRequest,
    config: SeekThumbnailConfig,
): SeekThumbnailProvider?
