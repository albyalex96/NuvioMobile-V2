package com.nuvio.app.features.downloads.hls

import kotlinx.serialization.Serializable

/**
 * What to download from an HLS stream, chosen by the user in the HLS download dialog and saved
 * with the download so the background transfer can resume it.
 */
@Serializable
data class HlsDownloadSpec(
    /** Media playlist of the chosen video version (the stream URL itself when it has no variants). */
    val videoPlaylistUrl: String,
    /** Keep the audio carried inside the video segments. */
    val includeMuxedAudio: Boolean = true,
    val muxedAudioLanguage: String? = null,
    val muxedAudioName: String? = null,
    /** Separate audio renditions to add as tracks. */
    val audio: List<HlsDownloadTrack> = emptyList(),
    /** WebVTT renditions: embedded in MKV, saved next to the video for MP4. */
    val subtitles: List<HlsDownloadTrack> = emptyList(),
    val bandwidth: Long? = null,
    val durationSeconds: Double? = null,
    /** Container predicted from the playlist codecs; the converter switches it if the tracks need it. */
    val container: HlsOutputContainer = HlsOutputContainer.Mp4,
)

@Serializable
data class HlsDownloadTrack(
    val playlistUrl: String,
    val language: String? = null,
    val name: String? = null,
    val label: String,
    val isDefault: Boolean = false,
    val forced: Boolean = false,
)

@Serializable
enum class HlsOutputContainer(val extension: String) {
    Mp4("mp4"),
    Mkv("mkv"),
}

/**
 * Codecs of an HLS `CODECS` attribute that the MP4 writer cannot store (AC-3, E-AC-3, MP3, DTS,
 * FLAC…). Their presence means the file is likely to end up as MKV.
 */
fun hlsMp4IncompatibleCodecs(codecs: List<String>): List<String> =
    codecs.filter { codec -> hlsCodecMp4Compatibility(codec) == false }

/** True when [codec] fits MP4, false when it needs MKV, null for subtitle/unknown codecs. */
fun hlsCodecMp4Compatibility(codec: String): Boolean? {
    val value = codec.trim().lowercase()
    return when {
        value.startsWith("wvtt") || value.startsWith("stpp") -> null
        value.startsWith("avc1") || value.startsWith("avc3") || value.startsWith("hvc1") ||
            value.startsWith("hev1") || value.startsWith("av01") || value.startsWith("vp09") ||
            value.startsWith("dvh1") || value.startsWith("dvhe") -> true
        value == "mp4a.40.34" || value == "mp4a.69" || value == "mp4a.6b" -> false
        value.startsWith("mp4a.40") -> true
        value == "opus" -> true
        value.startsWith("ac-3") || value.startsWith("ec-3") || value.startsWith("ac-4") ||
            value.startsWith("dts") || value == "flac" || value == "alac" || value.startsWith("mp4a") -> false
        else -> false
    }
}

/** The container predicted from the variant codecs: MP4 unless a codec needs MKV or none is declared. */
fun predictHlsContainer(codecs: List<String>): HlsOutputContainer {
    val media = codecs.filter { hlsCodecMp4Compatibility(it) != null }
    return if (media.isNotEmpty() && media.all { hlsCodecMp4Compatibility(it) == true }) {
        HlsOutputContainer.Mp4
    } else {
        HlsOutputContainer.Mkv
    }
}

/** Short, readable name of an HLS codec string ("ec-3" → "E-AC-3"). */
fun hlsCodecDisplayName(codec: String): String {
    val value = codec.trim().lowercase()
    return when {
        value.startsWith("avc1") || value.startsWith("avc3") -> "H.264"
        value.startsWith("hvc1") || value.startsWith("hev1") -> "HEVC"
        value.startsWith("dvh1") || value.startsWith("dvhe") -> "Dolby Vision"
        value.startsWith("av01") -> "AV1"
        value.startsWith("vp09") -> "VP9"
        value == "mp4a.40.34" || value == "mp4a.69" || value == "mp4a.6b" -> "MP3"
        value.startsWith("mp4a") -> "AAC"
        value.startsWith("ac-3") -> "AC-3"
        value.startsWith("ec-3") -> "E-AC-3"
        value.startsWith("ac-4") -> "AC-4"
        value.startsWith("dts") -> "DTS"
        value == "opus" -> "Opus"
        value == "flac" -> "FLAC"
        else -> codec.trim()
    }
}

internal fun isHlsVideoCodec(codec: String): Boolean {
    val value = codec.trim().lowercase()
    return value.startsWith("avc") || value.startsWith("hvc1") || value.startsWith("hev1") ||
        value.startsWith("av01") || value.startsWith("vp09") || value.startsWith("dvh") || value.startsWith("vp8")
}

internal fun isHlsSubtitleCodec(codec: String): Boolean {
    val value = codec.trim().lowercase()
    return value.startsWith("wvtt") || value.startsWith("stpp")
}
