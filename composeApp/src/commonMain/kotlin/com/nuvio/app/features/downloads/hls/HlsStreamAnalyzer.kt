package com.nuvio.app.features.downloads.hls

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

enum class HlsStreamProblem {
    /** No `#EXT-X-ENDLIST`: a live stream never ends. */
    Live,
    /** SAMPLE-AES, FairPlay, Widevine…: the segments cannot be decrypted. */
    Drm,
    /** The playlist could not be downloaded or is not an HLS playlist. */
    Unreadable,
    /** The playlist has no segments. */
    Empty,
}

data class HlsStreamAnalysis(
    /** Final URL of the stream playlist, after redirects. */
    val playlistUrl: String,
    /** Null when the stream URL is directly a media playlist. */
    val multivariant: HlsMultivariantPlaylist?,
    /** Media playlist of the first video version, used for the checks and the duration. */
    val firstMedia: HlsMediaPlaylist?,
    val problem: HlsStreamProblem?,
)

object HlsStreamAnalyzer {
    private val log = Logger.withTag("HlsDownload")
    private const val MAX_PLAYLIST_BYTES = 16 * 1024 * 1024
    private const val PROBE_TIMEOUT_MS = 10_000L
    private val progressiveExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "m4v", "wmv", "flv", "mpg", "mpeg", "ts", "m2ts", "3gp", "mp3", "m4a",
    )

    /** True when the URL path or query names an HLS playlist. */
    fun isHlsUrl(url: String): Boolean {
        val normalized = url.trim().lowercase()
        val path = normalized.substringBefore('?').substringBefore('#')
        return path.endsWith(".m3u8") || path.endsWith(".m3u") ||
            Regex("[?&](format|type|ext)=m3u8\\b").containsMatchIn(normalized)
    }

    private fun hasProgressiveExtension(url: String): Boolean =
        url.trim().lowercase().substringBefore('?').substringBefore('#').substringAfterLast('/')
            .substringAfterLast('.', "") in progressiveExtensions

    /**
     * Whether [url] serves an HLS playlist. URLs without a telling extension are checked by
     * reading the first bytes of the response (Content-Type or `#EXTM3U`); any error means "no".
     */
    suspend fun isHlsStream(url: String, headers: Map<String, String>): Boolean {
        if (isHlsUrl(url)) return true
        if (hasProgressiveExtension(url)) return false
        return try {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                val response = httpRequestRaw(
                    method = "GET",
                    url = url,
                    headers = headers + ("Range" to "bytes=0-4095"),
                    body = "",
                    maxResponseBodyBytes = 4096,
                )
                val contentType = response.headers["content-type"].orEmpty().lowercase()
                val isHls = response.status in 200..299 &&
                    ("mpegurl" in contentType || HlsPlaylistParser.isPlaylist(response.body))
                if (isHls) log.i { "Stream detected as HLS from its response (type=$contentType)" }
                isHls
            } ?: false
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            log.w(error) { "Could not probe the stream type, downloading it as a file" }
            false
        }
    }

    suspend fun analyze(url: String, headers: Map<String, String>): HlsStreamAnalysis {
        val (finalUrl, text) = try {
            fetchPlaylist(url, headers)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            log.e(error) { "Could not read the HLS playlist" }
            return HlsStreamAnalysis(url, null, null, HlsStreamProblem.Unreadable)
        }
        if (!HlsPlaylistParser.isPlaylist(text)) {
            log.w { "The stream response is not an HLS playlist" }
            return HlsStreamAnalysis(finalUrl, null, null, HlsStreamProblem.Unreadable)
        }
        if (!HlsPlaylistParser.isMultivariant(text)) {
            val media = HlsPlaylistParser.parseMedia(text, finalUrl)
            log.i { "HLS media playlist: ${media.segments.size} segments, ${media.durationSeconds.toInt()} s" }
            return HlsStreamAnalysis(finalUrl, null, media, problemOf(media))
        }
        val multivariant = HlsPlaylistParser.parseMultivariant(text, finalUrl)
        log.i {
            "HLS playlist: ${multivariant.variants.size} variants, " +
                "${multivariant.renditions.count { it.type == HlsRenditionType.Audio }} audio and " +
                "${multivariant.renditions.count { it.type == HlsRenditionType.Subtitles }} subtitle renditions"
        }
        if (multivariant.sessionKeyMethods.any { it != HlsKeyMethod.None && it != HlsKeyMethod.Aes128 }) {
            log.w { "HLS stream protected by DRM (session key)" }
            return HlsStreamAnalysis(finalUrl, multivariant, null, HlsStreamProblem.Drm)
        }
        val first = multivariant.variants.maxByOrNull { (it.height ?: 0) * 1_000_000_000L + (it.bandwidth ?: 0L) }
            ?: return HlsStreamAnalysis(finalUrl, multivariant, null, HlsStreamProblem.Empty)
        val media = try {
            fetchMedia(first.uri, headers)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            log.e(error) { "Could not read the HLS video playlist" }
            return HlsStreamAnalysis(finalUrl, multivariant, null, HlsStreamProblem.Unreadable)
        }
        return HlsStreamAnalysis(finalUrl, multivariant, media, problemOf(media))
    }

    suspend fun fetchMedia(url: String, headers: Map<String, String>): HlsMediaPlaylist {
        val (finalUrl, text) = fetchPlaylist(url, headers)
        check(HlsPlaylistParser.isPlaylist(text)) { "Not an HLS playlist" }
        return HlsPlaylistParser.parseMedia(text, finalUrl)
    }

    fun problemOf(media: HlsMediaPlaylist): HlsStreamProblem? = when {
        media.segments.isEmpty() -> HlsStreamProblem.Empty
        !media.isComplete -> HlsStreamProblem.Live
        media.segments.any { segment -> segment.key?.isSupported == false } -> HlsStreamProblem.Drm
        else -> null
    }

    private suspend fun fetchPlaylist(url: String, headers: Map<String, String>): Pair<String, String> {
        val response = httpRequestRaw(
            method = "GET",
            url = url,
            headers = headers,
            body = "",
            maxResponseBodyBytes = MAX_PLAYLIST_BYTES,
        )
        check(response.status in 200..299) { "HTTP ${response.status}" }
        return response.url.ifBlank { url } to response.bodyBytes.decodeToString()
    }
}
