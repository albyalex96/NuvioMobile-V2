package com.nuvio.app.features.streams.parser

/**
 * Video resolution detected for a stream. When nothing is detected the stream
 * defaults to [SD].
 */
enum class StreamParserResolution(val label: String) {
    UHD_8K("8K"),
    UHD_4K("4K"),
    QHD("1440p"),
    FHD("1080p"),
    HD("720p"),
    SD("SD"),
}

/** Category of a technical chip; drives its colour and icon. */
enum class StreamParserTagKind {
    SOURCE,
    CAM,
    DOLBY_VISION,
    HDR,
    VISUAL,
    BIT_DEPTH,
    CODEC,
    AUDIO_PREMIUM,
    AUDIO,
    CHANNELS,
}

data class StreamParserTag(
    val kind: StreamParserTagKind,
    val label: String,
)

/** Audio languages; [MULTI] marks multi-audio releases without explicit languages. */
enum class StreamParserLanguage {
    ITALIAN,
    ENGLISH,
    FRENCH,
    GERMAN,
    SPANISH,
    LATIN_SPANISH,
    PORTUGUESE,
    JAPANESE,
    CHINESE,
    KOREAN,
    RUSSIAN,
    UKRAINIAN,
    POLISH,
    HUNGARIAN,
    CZECH,
    DUTCH,
    DANISH,
    FINNISH,
    NORWEGIAN,
    SWEDISH,
    ROMANIAN,
    GREEK,
    TURKISH,
    ARABIC,
    HINDI,
    MULTI,
}

/** Everything the Stream Parser card shows for a single stream. */
data class StreamParserInfo(
    val resolution: StreamParserResolution = StreamParserResolution.SD,
    val tags: List<StreamParserTag> = emptyList(),
    val languages: List<StreamParserLanguage> = emptyList(),
    val sizeBytes: Long? = null,
    val durationSeconds: Long? = null,
    val sourceLabel: String? = null,
    val isCached: Boolean = false,
    val isTorrent: Boolean = false,
    val isProxied: Boolean = false,
)
