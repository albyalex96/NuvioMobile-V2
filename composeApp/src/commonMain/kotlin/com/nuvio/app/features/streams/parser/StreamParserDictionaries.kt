package com.nuvio.app.features.streams.parser

/**
 * Alias dictionaries used by [GenericStreamParser]. Every pattern runs on text
 * prepared by [StreamParserText.prepare] (lowercase, emoji removed, dots kept
 * only between digits), so aliases are written in lowercase.
 */
internal object StreamParserDictionaries {
    private const val START = "(?<![a-z0-9])"
    private const val END = "(?![a-z0-9])"

    /** Whole-word alternation: `pattern` must not touch letters or digits. */
    private fun word(pattern: String): Regex = Regex("$START(?:$pattern)$END")

    /** Like [word] but the match may be glued to trailing digits (e.g. `ddp5.1`). */
    private fun wordAllowingDigits(pattern: String): Regex = Regex("$START(?:$pattern)(?![a-z])")

    data class Alias<T>(val value: T, val regex: Regex)

    // Order matters: the first matching resolution wins inside a field.
    val resolutions: List<Alias<StreamParserResolution>> = listOf(
        Alias(StreamParserResolution.UHD_8K, word("4320p|8k")),
        Alias(StreamParserResolution.UHD_4K, word("2160p|2160i|4k|uhd|ultra ?hd|4k uhd")),
        Alias(StreamParserResolution.QHD, word("1440p|2k|qhd")),
        Alias(StreamParserResolution.FHD, word("1080p|1080i|fhd|full ?hd")),
        Alias(StreamParserResolution.HD, word("720p|720i|hd ?ready")),
        Alias(StreamParserResolution.SD, word("576p|480p|360p|240p|sd|sdtv")),
    )

    /** Bare `hd` means 720p only when nothing more specific was found; `dts-hd` must not count. */
    val bareHd: Regex = Regex("(?<![a-z0-9-])hd(?![a-z0-9-])")

    // Sources: single value, first match in priority order wins.
    val sources: List<Alias<StreamParserTag>> = listOf(
        Alias(StreamParserTag(StreamParserTagKind.SOURCE, "REMUX"), word("remux|bd-?remux|bd remux|uhd-?remux")),
        Alias(
            StreamParserTag(StreamParserTagKind.CAM, "CAM"),
            word("cam|camrip|cam-?rip|hdcam|hd-?cam|telesync|hdts|hd-?ts|telecine|hdtc|hd-?tc"),
        ),
        Alias(
            StreamParserTag(StreamParserTagKind.SOURCE, "BluRay"),
            word("blu-?ray|bluray|blu ray|bdrip|bd-?rip|brrip|br-?rip|bd25|bd50|bdmv|bd"),
        ),
        Alias(StreamParserTag(StreamParserTagKind.SOURCE, "WEBRip"), word("web-?rip|webrip|web rip")),
        Alias(StreamParserTag(StreamParserTagKind.SOURCE, "WEB-DL"), word("web-?dl|webdl|web dl|web")),
        Alias(StreamParserTag(StreamParserTagKind.SOURCE, "HDTV"), word("hdtv|pdtv|hdtvrip|tvrip|dsr|satrip")),
        Alias(StreamParserTag(StreamParserTagKind.SOURCE, "DVD"), word("dvd-?rip|dvdrip|dvd|dvd5|dvd9|dvdscr")),
    )

    // Visual tags: union across fields, then reduced by [absorbedBy].
    val visualTags: List<Alias<StreamParserTag>> = listOf(
        Alias(StreamParserTag(StreamParserTagKind.DOLBY_VISION, "Dolby Vision"), word("dv|dovi|dolby ?vision")),
        Alias(StreamParserTag(StreamParserTagKind.HDR, "HDR10+"), Regex("${START}(?:hdr10\\+|hdr10 ?plus|hdr\\+)")),
        Alias(StreamParserTag(StreamParserTagKind.HDR, "HDR10"), Regex("${START}hdr10(?![a-z0-9+]| plus)")),
        Alias(StreamParserTag(StreamParserTagKind.HDR, "HDR"), Regex("${START}hdr(?![a-z0-9+])")),
        Alias(StreamParserTag(StreamParserTagKind.HDR, "HLG"), word("hlg")),
        Alias(StreamParserTag(StreamParserTagKind.VISUAL, "IMAX"), word("imax")),
        Alias(StreamParserTag(StreamParserTagKind.VISUAL, "3D"), word("3d|h-?sbs|h-?ou")),
        Alias(StreamParserTag(StreamParserTagKind.BIT_DEPTH, "12bit"), word("12 ?-?bits?")),
        Alias(StreamParserTag(StreamParserTagKind.BIT_DEPTH, "10bit"), word("10 ?-?bits?|hi10p?")),
    )

    val codecs: List<Alias<StreamParserTag>> = listOf(
        Alias(StreamParserTag(StreamParserTagKind.CODEC, "HEVC"), wordAllowingDigits("hevc|x ?265|h ?265")),
        Alias(StreamParserTag(StreamParserTagKind.CODEC, "AV1"), word("av1")),
        Alias(StreamParserTag(StreamParserTagKind.CODEC, "AVC"), wordAllowingDigits("avc|x ?264|h ?264")),
        Alias(StreamParserTag(StreamParserTagKind.CODEC, "VP9"), word("vp9")),
        Alias(StreamParserTag(StreamParserTagKind.CODEC, "XviD"), word("xvid|divx")),
        Alias(StreamParserTag(StreamParserTagKind.CODEC, "MPEG-2"), word("mpeg-?2")),
    )

    val audioTags: List<Alias<StreamParserTag>> = listOf(
        Alias(StreamParserTag(StreamParserTagKind.AUDIO_PREMIUM, "Atmos"), wordAllowingDigits("atmos")),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO_PREMIUM, "TrueHD"), wordAllowingDigits("true-?hd|true hd")),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO_PREMIUM, "DTS:X"), wordAllowingDigits("dts-?x|dts:x")),
        Alias(
            StreamParserTag(StreamParserTagKind.AUDIO_PREMIUM, "DTS-HD MA"),
            wordAllowingDigits("dts-?hd ?-?ma|dts-?hd master audio|dtshd ?-?ma|dts-?ma"),
        ),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO, "DTS-HD"), wordAllowingDigits("dts-?hd|dtshd")),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO, "DTS"), Regex("${START}dts(?![a-z]|-[a-z]|:x)")),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO, "DD+"), Regex("${START}(?:dd\\+|ddp|e-?ac-?3|dolby digital plus)(?![a-z])")),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO, "DD"), wordAllowingDigits("dd|ac-?3|dolby digital")),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO, "AAC"), wordAllowingDigits("aac|he-?aac")),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO, "FLAC"), wordAllowingDigits("flac")),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO, "LPCM"), wordAllowingDigits("l?pcm")),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO, "Opus"), wordAllowingDigits("opus")),
        Alias(StreamParserTag(StreamParserTagKind.AUDIO, "MP3"), wordAllowingDigits("mp3")),
    )

    /** `5.1`, `7.1`, `2.0`… also when glued to an audio codec (`ddp5.1`). Never part of a longer number. */
    val channels: Regex = Regex(
        "(?<![0-9.])([24567])\\.([012])(?![0-9.])(?!\\s?(?:tib|tb|gib|gb|mib|mb|kb|mbps|gbps|kbps|fps|ghz)(?![a-z]))",
    )

    /** Labels that make a more generic label redundant within the same stream. */
    val absorbedBy: Map<String, Set<String>> = mapOf(
        "HDR10+" to setOf("HDR10", "HDR"),
        "HDR10" to setOf("HDR"),
        "12bit" to setOf("10bit"),
        "DTS-HD MA" to setOf("DTS-HD", "DTS"),
        "DTS-HD" to setOf("DTS"),
        "DTS:X" to setOf("DTS"),
        "DD+" to setOf("DD"),
    )

    // --- Languages ---------------------------------------------------------------------

    /** ISO 3166 country of a flag emoji → audio language. */
    val flagLanguages: Map<String, StreamParserLanguage> = mapOf(
        "IT" to StreamParserLanguage.ITALIAN,
        "GB" to StreamParserLanguage.ENGLISH,
        "US" to StreamParserLanguage.ENGLISH,
        "FR" to StreamParserLanguage.FRENCH,
        "DE" to StreamParserLanguage.GERMAN,
        "ES" to StreamParserLanguage.SPANISH,
        "MX" to StreamParserLanguage.LATIN_SPANISH,
        "PT" to StreamParserLanguage.PORTUGUESE,
        "BR" to StreamParserLanguage.PORTUGUESE,
        "JP" to StreamParserLanguage.JAPANESE,
        "CN" to StreamParserLanguage.CHINESE,
        "TW" to StreamParserLanguage.CHINESE,
        "HK" to StreamParserLanguage.CHINESE,
        "KR" to StreamParserLanguage.KOREAN,
        "RU" to StreamParserLanguage.RUSSIAN,
        "UA" to StreamParserLanguage.UKRAINIAN,
        "PL" to StreamParserLanguage.POLISH,
        "HU" to StreamParserLanguage.HUNGARIAN,
        "CZ" to StreamParserLanguage.CZECH,
        "NL" to StreamParserLanguage.DUTCH,
        "DK" to StreamParserLanguage.DANISH,
        "FI" to StreamParserLanguage.FINNISH,
        "NO" to StreamParserLanguage.NORWEGIAN,
        "SE" to StreamParserLanguage.SWEDISH,
        "RO" to StreamParserLanguage.ROMANIAN,
        "GR" to StreamParserLanguage.GREEK,
        "TR" to StreamParserLanguage.TURKISH,
        "SA" to StreamParserLanguage.ARABIC,
        "AE" to StreamParserLanguage.ARABIC,
        "IN" to StreamParserLanguage.HINDI,
    )

    /** Single-token language words and codes (already lowercase). */
    val languageWords: Map<String, StreamParserLanguage> = buildMap {
        fun add(language: StreamParserLanguage, vararg words: String) = words.forEach { put(it, language) }
        add(StreamParserLanguage.ITALIAN, "ita", "italian", "italiano", "italiana")
        add(StreamParserLanguage.ENGLISH, "eng", "english", "inglese")
        add(StreamParserLanguage.FRENCH, "fre", "fra", "french", "francais", "français", "francese", "vff", "vfq", "truefrench")
        add(StreamParserLanguage.GERMAN, "ger", "deu", "german", "deutsch", "tedesco")
        add(StreamParserLanguage.SPANISH, "spa", "esp", "spanish", "espanol", "español", "castellano", "spagnolo")
        add(StreamParserLanguage.LATIN_SPANISH, "latino", "lat")
        add(StreamParserLanguage.PORTUGUESE, "por", "portuguese", "portugues", "português", "portoghese")
        add(StreamParserLanguage.JAPANESE, "jpn", "jap", "japanese", "giapponese")
        add(StreamParserLanguage.CHINESE, "chi", "zho", "chs", "cht", "chinese", "mandarin", "cantonese", "cinese")
        add(StreamParserLanguage.KOREAN, "kor", "korean", "coreano")
        add(StreamParserLanguage.RUSSIAN, "rus", "russian", "russo")
        add(StreamParserLanguage.UKRAINIAN, "ukr", "ukrainian", "ucraino")
        add(StreamParserLanguage.POLISH, "pol", "polish", "polacco")
        add(StreamParserLanguage.HUNGARIAN, "hun", "hungarian", "ungherese")
        add(StreamParserLanguage.CZECH, "cze", "ces", "czech", "ceco")
        add(StreamParserLanguage.DUTCH, "dut", "nld", "dutch", "olandese")
        add(StreamParserLanguage.DANISH, "dan", "danish", "danese")
        add(StreamParserLanguage.FINNISH, "fin", "finnish", "finlandese")
        add(StreamParserLanguage.NORWEGIAN, "nor", "norwegian", "norvegese")
        add(StreamParserLanguage.SWEDISH, "swe", "swedish", "svedese")
        add(StreamParserLanguage.ROMANIAN, "rum", "ron", "romanian", "rumeno")
        add(StreamParserLanguage.GREEK, "gre", "ell", "greek", "greco")
        add(StreamParserLanguage.TURKISH, "tur", "turkish", "turco")
        add(StreamParserLanguage.ARABIC, "ara", "arabic", "arabo")
        add(StreamParserLanguage.HINDI, "hin", "hindi")
        add(StreamParserLanguage.MULTI, "multi", "multiaudio", "multilang", "multilanguage")
    }

    /** Tokens that start a subtitle-language list (`sub ita eng`, `subs: eng`). */
    val subtitleMarkers: Set<String> = setOf(
        "sub", "subs", "subbed", "subtitle", "subtitles", "sottotitoli", "sottotitolo", "softsub", "hardsub", "forced",
    )

    /** Fused forms such as `subita`, `subeng`. */
    val fusedSubtitlePrefix: Regex = Regex("^(?:sub|subs)([a-z]{3})$")

    /** Tokens that may separate items of a language list without ending it. */
    val languageListGlue: Set<String> = setOf("e", "and", "&", "+")

    /**
     * Marks the start of release metadata inside a line (year, resolution, SxxEyy).
     * Language words before it belong to the title and are ignored.
     */
    val releaseMetadataStart: Regex = Regex(
        "$START(?:19[0-9]{2}|20[0-9]{2}|s[0-9]{1,2}e[0-9]{1,3}|s[0-9]{1,2}|[0-9]{3,4}p|4k|uhd)$END",
    )

    // --- Raw-text markers (checked on lowercase original text, emoji preserved) ----------

    /** Subtitle emoji markers: flags that follow are subtitle languages, not audio. */
    val subtitleEmojiMarkers: Set<Int> = setOf(0x1F4DD, 0x1F4AC) // 📝 💬

    val cachedNegative: Regex = Regex(
        "⏳|\\buncached\\b|\\bnot cached\\b|\\[(?:rd|ad|tb|pm|dl|ed|oc|pk|tr)\\s*download\\]|\\[(?:rd|ad|tb|pm|dl|ed|oc|pk|tr)\\s*⏳\\]",
    )

    val cachedPositive: Regex = Regex(
        "⚡|\\binstant\\b|\\bcached\\b|\\bready\\s*\\((?:rd|ad|tb|pm|dl|ed|oc|pk|tr)\\)|\\[(?:rd|ad|tb|pm|dl|ed|oc|pk|tr)\\+\\]",
    )

    val p2p: Regex = Regex("\\bp2p\\b")

    val proxyNegative: Regex = Regex("\\bproxy\\s*[(\\[:]?\\s*(?:off|disabled|no|false|disattivato)\\b")

    val proxyPositive: Regex = Regex(
        "\\bproxied\\b|\\bproxy\\s*[(\\[:]?\\s*(?:on|enabled|active|yes|true|attivo)\\b|\\bvia proxy\\b|\\bproxy\\b",
    )

    /** Explicit source/provider markers: `🔍Name`, `⚙️ Name`, `Source: Name`. */
    val sourceMarkers: List<Regex> = listOf(
        Regex("🔍\\uFE0F?\\s*([^\\n|•]+)"),
        Regex("⚙\\uFE0F?\\s*([^\\n|•]+)"),
        Regex("(?i)\\b(?:source|provider|fonte)\\s*[:：]\\s*([^\\n|•]+)"),
    )

    val size: Regex = Regex("(?i)(?<![0-9.,])([0-9]+(?:[.,][0-9]+)?)\\s?(tib|tb|gib|gb|mib|mb)(?![a-z/])")

    val durationHms: Regex = Regex("(?i)(?<![0-9])([0-9]{1,2})\\s?h\\s?[: ]?\\s?([0-9]{1,2})\\s?m(?:in)?(?:\\s?[: ]?\\s?([0-9]{1,2})\\s?s)?")

    val durationMinutes: Regex = Regex("(?i)(?<![0-9])([0-9]{2,3})\\s?(?:min|mins|minutes|minuti)\\b")
}
