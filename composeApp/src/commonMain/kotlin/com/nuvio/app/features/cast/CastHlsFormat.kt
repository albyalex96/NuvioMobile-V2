package com.nuvio.app.features.cast

import co.touchlab.kermit.Logger
import com.nuvio.app.features.downloads.hls.HlsMediaPlaylist
import com.nuvio.app.features.downloads.hls.HlsRenditionType
import com.nuvio.app.features.downloads.hls.HlsStreamAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private val log = Logger.withTag("Cast")
private const val HLS_FORMAT_PROBE_TIMEOUT_MS = 8_000L

/** Container of the segments of an HLS media playlist. */
enum class CastHlsSegmentFormat { Ts, Fmp4, Aac, Ac3, Eac3, Mp3 }

/**
 * What the receiver must be told about an HLS stream: the Default Media Receiver assumes MPEG-TS
 * segments and fails on fMP4 (CMAF) or packed audio unless the formats are declared.
 *
 * @property audio format of the separate audio rendition, or null when the audio is in the video.
 */
data class CastHlsFormats(
    val video: CastHlsSegmentFormat,
    val audio: CastHlsSegmentFormat? = null,
)

/**
 * Format of the segments of [media]. fMP4 HLS always declares an `EXT-X-MAP`: without it the
 * segments are MPEG-TS whatever their extension (sites disguise them as ".html", ".png" or even
 * ".m4s"), unless they are packed audio.
 */
internal fun castHlsSegmentFormatOf(media: HlsMediaPlaylist): CastHlsSegmentFormat? {
    val segment = media.segments.firstOrNull { !it.isGap } ?: return null
    if (segment.initSegment != null) return CastHlsSegmentFormat.Fmp4
    val extension = segment.uri.substringBefore('?').substringBefore('#')
        .substringAfterLast('/').substringAfterLast('.', "").lowercase()
    return when (extension) {
        "aac", "adts" -> CastHlsSegmentFormat.Aac
        "ac3" -> CastHlsSegmentFormat.Ac3
        "ec3", "eac3" -> CastHlsSegmentFormat.Eac3
        "mp3" -> CastHlsSegmentFormat.Mp3
        else -> CastHlsSegmentFormat.Ts
    }
}

/**
 * Reads the playlists of [url] to find the segment formats. Null when they cannot be read in
 * time: the receiver then keeps its defaults.
 */
suspend fun detectCastHlsFormats(url: String, headers: Map<String, String>): CastHlsFormats? = try {
    withTimeoutOrNull(HLS_FORMAT_PROBE_TIMEOUT_MS) {
        val analysis = HlsStreamAnalyzer.analyze(url, headers)
        val videoMedia = analysis.firstMedia ?: return@withTimeoutOrNull null
        val video = castHlsSegmentFormatOf(videoMedia) ?: return@withTimeoutOrNull null
        val multivariant = analysis.multivariant
        // Same version HlsStreamAnalyzer read as firstMedia.
        val variant = multivariant?.variants?.maxByOrNull { (it.height ?: 0) * 1_000_000_000L + (it.bandwidth ?: 0L) }
        val audioRendition = multivariant?.renditions
            ?.filter { it.type == HlsRenditionType.Audio && it.groupId == variant?.audioGroupId && it.uri != null }
            ?.let { renditions -> renditions.firstOrNull { it.isDefault } ?: renditions.firstOrNull() }
        val audio = audioRendition?.uri?.let { audioUrl ->
            runCatching { castHlsSegmentFormatOf(HlsStreamAnalyzer.fetchMedia(audioUrl, headers)) }
                .onFailure { error -> if (error is CancellationException) throw error }
                .getOrNull()
        }
        CastHlsFormats(video = video, audio = audio)
    }
} catch (error: Exception) {
    if (error is CancellationException) throw error
    log.w(error) { "Could not read the HLS playlists to find the segment format" }
    null
}
