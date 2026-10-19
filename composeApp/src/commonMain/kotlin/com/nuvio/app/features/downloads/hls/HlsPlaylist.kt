package com.nuvio.app.features.downloads.hls

/** A multivariant ("master") playlist: the video versions and their alternative renditions. */
data class HlsMultivariantPlaylist(
    val variants: List<HlsVariant>,
    val renditions: List<HlsRendition>,
    /** Key formats of `#EXT-X-SESSION-KEY` tags, used to detect DRM before reading media playlists. */
    val sessionKeyMethods: List<HlsKeyMethod> = emptyList(),
)

data class HlsVariant(
    val uri: String,
    val bandwidth: Long?,
    val averageBandwidth: Long?,
    val codecs: List<String>,
    val width: Int?,
    val height: Int?,
    val frameRate: Double?,
    val videoRange: String?,
    val audioGroupId: String?,
    val subtitlesGroupId: String?,
)

enum class HlsRenditionType { Audio, Subtitles, Video, ClosedCaptions }

data class HlsRendition(
    val type: HlsRenditionType,
    val groupId: String,
    val name: String?,
    val language: String?,
    /** Null when the rendition is carried inside the variant stream itself. */
    val uri: String?,
    val isDefault: Boolean,
    val autoSelect: Boolean,
    val forced: Boolean,
    val channels: String?,
    val characteristics: String?,
)

data class HlsMediaPlaylist(
    val segments: List<HlsSegment>,
    val targetDurationSeconds: Double?,
    val mediaSequence: Long,
    val hasEndList: Boolean,
    val playlistType: String?,
) {
    /** A VOD playlist has all its segments: `#EXT-X-ENDLIST` or `#EXT-X-PLAYLIST-TYPE:VOD`. */
    val isComplete: Boolean
        get() = hasEndList || playlistType.equals("VOD", ignoreCase = true)

    val durationSeconds: Double
        get() = segments.sumOf { it.durationSeconds }

    val discontinuityCount: Int
        get() = (segments.maxOfOrNull { it.discontinuityIndex } ?: -1) + 1
}

data class HlsByteRange(val offset: Long, val length: Long)

data class HlsSegment(
    val uri: String,
    val durationSeconds: Double,
    val mediaSequence: Long,
    /** Increases at every `#EXT-X-DISCONTINUITY`: segments with the same index share one timeline. */
    val discontinuityIndex: Int,
    val byteRange: HlsByteRange?,
    val key: HlsKey?,
    val initSegment: HlsInitSegment?,
    val isGap: Boolean = false,
)

data class HlsInitSegment(val uri: String, val byteRange: HlsByteRange?)

enum class HlsKeyMethod { None, Aes128, SampleAes, SampleAesCtr, Other }

data class HlsKey(
    val method: HlsKeyMethod,
    val uri: String?,
    /** The explicit IV, 16 bytes; null means "use the media sequence number". */
    val iv: ByteArray?,
    val keyFormat: String,
) {
    /** Only clear segments and AES-128 with an identity key can be downloaded. */
    val isSupported: Boolean
        get() = method == HlsKeyMethod.None ||
            (method == HlsKeyMethod.Aes128 && keyFormat.equals("identity", ignoreCase = true) && uri != null)

    override fun equals(other: Any?): Boolean =
        other is HlsKey && method == other.method && uri == other.uri && keyFormat == other.keyFormat &&
            (iv?.contentEquals(other.iv) ?: (other.iv == null))

    override fun hashCode(): Int = ((method.hashCode() * 31 + (uri?.hashCode() ?: 0)) * 31 + keyFormat.hashCode()) * 31 +
        (iv?.contentHashCode() ?: 0)
}

object HlsPlaylistParser {
    fun isPlaylist(text: String): Boolean = text.trimStart('﻿', ' ', '\t', '\r', '\n').startsWith("#EXTM3U")

    fun isMultivariant(text: String): Boolean = text.lineSequence().any { it.trim().startsWith("#EXT-X-STREAM-INF") }

    fun parseMultivariant(text: String, baseUrl: String): HlsMultivariantPlaylist {
        val variants = mutableListOf<HlsVariant>()
        val renditions = mutableListOf<HlsRendition>()
        val sessionKeys = mutableListOf<HlsKeyMethod>()
        var pendingVariant: Map<String, String>? = null
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-STREAM-INF:") -> pendingVariant = parseAttributes(line.substringAfter(':'))
                line.startsWith("#EXT-X-MEDIA:") -> parseRendition(parseAttributes(line.substringAfter(':')), baseUrl)
                    ?.let(renditions::add)
                line.startsWith("#EXT-X-SESSION-KEY:") -> sessionKeys += parseAttributes(line.substringAfter(':'))
                    .keyMethod()
                line.startsWith("#") -> Unit
                else -> pendingVariant?.let { attributes ->
                    variants += parseVariant(attributes, resolveHlsUrl(baseUrl, line))
                    pendingVariant = null
                }
            }
        }
        return HlsMultivariantPlaylist(variants, renditions, sessionKeys)
    }

    fun parseMedia(text: String, baseUrl: String): HlsMediaPlaylist {
        val segments = mutableListOf<HlsSegment>()
        var targetDuration: Double? = null
        var mediaSequence = 0L
        var hasEndList = false
        var playlistType: String? = null
        var discontinuityIndex = 0
        var pendingDuration: Double? = null
        var pendingRange: Pair<Long, Long?>? = null
        var pendingGap = false
        var currentKey: HlsKey? = null
        var currentInit: HlsInitSegment? = null
        // A byte range without offset starts right after the previous range of the same resource.
        val nextRangeOffset = mutableMapOf<String, Long>()
        var sawFirstSegment = false

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXT-X-TARGETDURATION:") -> targetDuration = line.substringAfter(':').trim().toDoubleOrNull()
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> mediaSequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                line.startsWith("#EXT-X-PLAYLIST-TYPE:") -> playlistType = line.substringAfter(':').trim()
                line == "#EXT-X-ENDLIST" -> hasEndList = true
                line == "#EXT-X-DISCONTINUITY" -> if (sawFirstSegment) discontinuityIndex++
                line == "#EXT-X-GAP" -> pendingGap = true
                line.startsWith("#EXTINF:") -> pendingDuration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
                line.startsWith("#EXT-X-BYTERANGE:") -> pendingRange = parseByteRange(line.substringAfter(':'))
                line.startsWith("#EXT-X-KEY:") -> {
                    val attributes = parseAttributes(line.substringAfter(':'))
                    val method = attributes.keyMethod()
                    currentKey = if (method == HlsKeyMethod.None) null else HlsKey(
                        method = method,
                        uri = attributes["URI"]?.let { resolveHlsUrl(baseUrl, it) },
                        iv = attributes["IV"]?.let(::parseHexIv),
                        keyFormat = attributes["KEYFORMAT"] ?: "identity",
                    )
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val attributes = parseAttributes(line.substringAfter(':'))
                    currentInit = attributes["URI"]?.let { uri ->
                        val range = attributes["BYTERANGE"]?.let(::parseByteRange)
                        HlsInitSegment(
                            uri = resolveHlsUrl(baseUrl, uri),
                            byteRange = range?.let { (length, offset) -> HlsByteRange(offset ?: 0L, length) },
                        )
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val uri = resolveHlsUrl(baseUrl, line)
                    val byteRange = pendingRange?.let { (length, offset) ->
                        val start = offset ?: nextRangeOffset[uri] ?: 0L
                        nextRangeOffset[uri] = start + length
                        HlsByteRange(start, length)
                    }
                    segments += HlsSegment(
                        uri = uri,
                        durationSeconds = pendingDuration ?: 0.0,
                        mediaSequence = mediaSequence + segments.size,
                        discontinuityIndex = discontinuityIndex,
                        byteRange = byteRange,
                        key = currentKey,
                        initSegment = currentInit,
                        isGap = pendingGap,
                    )
                    sawFirstSegment = true
                    pendingDuration = null
                    pendingRange = null
                    pendingGap = false
                }
            }
        }
        return HlsMediaPlaylist(segments, targetDuration, mediaSequence, hasEndList, playlistType)
    }

    private fun parseVariant(attributes: Map<String, String>, uri: String): HlsVariant {
        val resolution = attributes["RESOLUTION"]?.lowercase()?.split('x')
        return HlsVariant(
            uri = uri,
            bandwidth = attributes["BANDWIDTH"]?.toLongOrNull(),
            averageBandwidth = attributes["AVERAGE-BANDWIDTH"]?.toLongOrNull(),
            codecs = parseCodecs(attributes["CODECS"]),
            width = resolution?.getOrNull(0)?.trim()?.toIntOrNull(),
            height = resolution?.getOrNull(1)?.trim()?.toIntOrNull(),
            frameRate = attributes["FRAME-RATE"]?.toDoubleOrNull(),
            videoRange = attributes["VIDEO-RANGE"],
            audioGroupId = attributes["AUDIO"],
            subtitlesGroupId = attributes["SUBTITLES"],
        )
    }

    private fun parseRendition(attributes: Map<String, String>, baseUrl: String): HlsRendition? {
        val type = when (attributes["TYPE"]?.uppercase()) {
            "AUDIO" -> HlsRenditionType.Audio
            "SUBTITLES" -> HlsRenditionType.Subtitles
            "VIDEO" -> HlsRenditionType.Video
            "CLOSED-CAPTIONS" -> HlsRenditionType.ClosedCaptions
            else -> return null
        }
        return HlsRendition(
            type = type,
            groupId = attributes["GROUP-ID"] ?: return null,
            name = attributes["NAME"]?.takeIf { it.isNotBlank() },
            language = attributes["LANGUAGE"]?.trim()?.takeIf { it.isNotBlank() },
            uri = attributes["URI"]?.takeIf { it.isNotBlank() && type != HlsRenditionType.ClosedCaptions }
                ?.let { resolveHlsUrl(baseUrl, it) },
            isDefault = attributes["DEFAULT"].equals("YES", ignoreCase = true),
            autoSelect = attributes["AUTOSELECT"].equals("YES", ignoreCase = true),
            forced = attributes["FORCED"].equals("YES", ignoreCase = true),
            channels = attributes["CHANNELS"]?.takeIf { it.isNotBlank() },
            characteristics = attributes["CHARACTERISTICS"]?.takeIf { it.isNotBlank() },
        )
    }

    /** "1000@20" → (length 1000, offset 20); "1000" → (1000, null). */
    private fun parseByteRange(value: String): Pair<Long, Long?>? {
        val trimmed = value.trim().trim('"')
        val length = trimmed.substringBefore('@').trim().toLongOrNull() ?: return null
        val offset = trimmed.substringAfter('@', "").trim().toLongOrNull()
        return length to offset
    }

    private fun Map<String, String>.keyMethod(): HlsKeyMethod = when (this["METHOD"]?.uppercase()) {
        null, "NONE" -> HlsKeyMethod.None
        "AES-128" -> HlsKeyMethod.Aes128
        "SAMPLE-AES" -> HlsKeyMethod.SampleAes
        "SAMPLE-AES-CTR" -> HlsKeyMethod.SampleAesCtr
        else -> HlsKeyMethod.Other
    }

    private fun parseHexIv(value: String): ByteArray? {
        val hex = value.trim().removePrefix("0x").removePrefix("0X")
        if (hex.isEmpty() || hex.length > 32 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val padded = hex.padStart(32, '0')
        return ByteArray(16) { index -> padded.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    internal fun parseCodecs(value: String?): List<String> =
        value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Attribute list as in RFC 8216 §4.2: `KEY=value,KEY="quoted, value"`. Keys are upper-cased. */
    internal fun parseAttributes(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var index = 0
        while (index < text.length) {
            while (index < text.length && (text[index] == ',' || text[index] == ' ')) index++
            val equals = text.indexOf('=', index)
            if (equals < 0) break
            val key = text.substring(index, equals).trim().uppercase()
            index = equals + 1
            val value: String
            if (index < text.length && text[index] == '"') {
                val end = text.indexOf('"', index + 1).let { if (it < 0) text.length else it }
                value = text.substring(index + 1, end)
                index = end + 1
            } else {
                val end = text.indexOf(',', index).let { if (it < 0) text.length else it }
                value = text.substring(index, end).trim()
                index = end
            }
            if (key.isNotEmpty()) result[key] = value
        }
        return result
    }
}

/** Resolves [reference] against [base] (RFC 3986 §5.2, enough for playlist URIs). */
fun resolveHlsUrl(base: String, reference: String): String {
    val ref = reference.trim()
    if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(ref)) return ref
    val schemeEnd = base.indexOf("://")
    if (schemeEnd < 0) return ref
    val scheme = base.substring(0, schemeEnd)
    if (ref.startsWith("//")) return "$scheme:$ref"
    val authorityStart = schemeEnd + 3
    val pathStart = base.indexOfAny(charArrayOf('/', '?', '#'), authorityStart).let { if (it < 0) base.length else it }
    val origin = base.substring(0, pathStart)
    val basePathAndQuery = base.substring(pathStart).substringBefore('#')
    val basePath = basePathAndQuery.substringBefore('?')
    return when {
        ref.isEmpty() -> origin + basePathAndQuery
        ref.startsWith("#") -> origin + basePathAndQuery + ref
        ref.startsWith("?") -> origin + basePath + ref
        ref.startsWith("/") -> origin + removeDotSegments(ref)
        else -> {
            val directory = if (basePath.isEmpty()) "/" else basePath.substring(0, basePath.lastIndexOf('/') + 1)
            origin + removeDotSegments(directory + ref)
        }
    }
}

private fun removeDotSegments(pathWithQuery: String): String {
    val path = pathWithQuery.substringBefore('?').substringBefore('#')
    val suffix = pathWithQuery.substring(path.length)
    val output = mutableListOf<String>()
    val parts = path.split('/')
    parts.forEachIndexed { index, part ->
        when (part) {
            "." -> if (index == parts.lastIndex) output += ""
            ".." -> {
                if (output.size > 1) output.removeAt(output.lastIndex)
                if (index == parts.lastIndex) output += ""
            }
            else -> output += part
        }
    }
    val joined = output.joinToString("/")
    return (if (joined.startsWith("/")) joined else "/$joined") + suffix
}
