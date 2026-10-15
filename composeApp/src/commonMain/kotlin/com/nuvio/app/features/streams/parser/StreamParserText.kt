package com.nuvio.app.features.streams.parser

/** Text helpers shared by [GenericStreamParser]. Implemented without Unicode regex classes for Kotlin/Native parity. */
internal object StreamParserText {
    private const val REGIONAL_INDICATOR_A = 0x1F1E6
    private const val REGIONAL_INDICATOR_Z = 0x1F1FF
    private const val VARIATION_SELECTOR = 0xFE0F
    private const val ZERO_WIDTH_JOINER = 0x200D

    /**
     * Lowercases [raw], drops apostrophes, turns emoji and punctuation into spaces and keeps
     * `+ - :` plus dots that sit between two digits (`5.1`, `58.3`), so `H.265` and
     * `The.Movie.2160p` become space separated while channel layouts survive.
     */
    fun prepare(raw: String): String {
        val builder = StringBuilder(raw.length)
        for (char in raw) {
            when {
                char == '\'' || char == '’' || char == '`' -> Unit
                char.isLetterOrDigit() -> builder.append(char.lowercaseChar())
                char == '+' || char == '-' || char == ':' || char == '.' -> builder.append(char)
                char == '\n' -> builder.append('\n')
                else -> builder.append(' ')
            }
        }
        val withDots = builder.toString()
        val result = StringBuilder(withDots.length)
        for (index in withDots.indices) {
            val char = withDots[index]
            if (char == '.') {
                val previousIsDigit = index > 0 && withDots[index - 1].isDigit()
                val nextIsDigit = index + 1 < withDots.length && withDots[index + 1].isDigit()
                result.append(if (previousIsDigit && nextIsDigit) '.' else ' ')
            } else {
                result.append(char)
            }
        }
        return result.toString()
            .lines()
            .joinToString("\n") { line -> line.split(' ').filter { it.isNotEmpty() }.joinToString(" ") }
            .trim()
    }

    /** Splits prepared text into alphanumeric tokens. */
    fun tokens(prepared: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        for (char in prepared) {
            if (char.isLetterOrDigit()) {
                current.append(char)
            } else if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) tokens += current.toString()
        return tokens
    }

    /**
     * Removes the content title and anything before the first release-metadata token
     * (year, resolution, SxxEyy) so title words are not mistaken for tags or languages.
     */
    fun stripTitle(preparedLine: String, preparedKnownTitle: String?): String {
        var line = preparedLine
        if (!preparedKnownTitle.isNullOrBlank() && preparedKnownTitle.length >= 2) {
            line = line.replace(preparedKnownTitle, " ")
        }
        val metadataStart = StreamParserDictionaries.releaseMetadataStart.find(line)
        if (metadataStart != null && metadataStart.range.first > 0) {
            line = line.substring(metadataStart.range.first)
        }
        return line.trim()
    }

    /**
     * Returns ISO country codes of the flag emoji in [raw], in order, skipping the flags
     * that follow a subtitle marker (📝, 💬) because those are subtitle languages.
     */
    fun audioFlagCountries(raw: String): List<String> {
        val countries = mutableListOf<String>()
        var inSubtitleList = false
        var index = 0
        while (index < raw.length) {
            val codePoint = codePointAt(raw, index)
            val width = if (codePoint > 0xFFFF) 2 else 1
            when {
                codePoint in StreamParserDictionaries.subtitleEmojiMarkers -> inSubtitleList = true
                codePoint in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_Z -> {
                    val nextIndex = index + width
                    val next = if (nextIndex < raw.length) codePointAt(raw, nextIndex) else -1
                    if (next in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_Z) {
                        if (!inSubtitleList) {
                            val first = 'A' + (codePoint - REGIONAL_INDICATOR_A)
                            val second = 'A' + (next - REGIONAL_INDICATOR_A)
                            countries += "$first$second"
                        }
                        index = nextIndex + if (next > 0xFFFF) 2 else 1
                        continue
                    }
                }
                codePoint == VARIATION_SELECTOR || codePoint == ZERO_WIDTH_JOINER -> Unit
                codePoint < 0x80 && (raw[index].isWhitespace() || raw[index] in "/|,&+-") -> Unit
                else -> inSubtitleList = false
            }
            index += width
        }
        return countries
    }

    /**
     * BMP emoji and pictographic symbols: arrows, misc symbols and dingbats (⚡ ⏳ ✅ ⚙ …), plus
     * the invisible joiners/selectors that glue emoji together.
     */
    private val bmpEmoji = Regex("[\\u2190-\\u2BFF\\u2300-\\u23FF\\u3030\\u303D\\u3297\\u3299\\uFE0E\\uFE0F\\u200D\\u20E3]")
    private val whitespace = Regex("\\s+")

    /**
     * Cleans a provider/addon name: stops at the first separator (`|`, `•`, newline), removes
     * every emoji wherever it appears and collapses whitespace. `null` when nothing usable is left.
     *
     * Emoji above U+FFFF (🔍 🇮🇹 🎬 …) are surrogate pairs: they are dropped char by char because
     * JVM regex matches whole code points while Kotlin/Native matches UTF-16 units.
     */
    fun cleanLabel(raw: String): String? {
        val firstSegment = raw.split('\n', '|', '•').first()
        val withoutAstralEmoji = buildString(firstSegment.length) {
            firstSegment.forEach { char -> append(if (char.isSurrogate()) ' ' else char) }
        }
        return withoutAstralEmoji
            .replace(bmpEmoji, " ")
            .replace(whitespace, " ")
            .trim()
            .trim('-', ':', '.', ',', '[', ']', '(', ')')
            .trim()
            .takeIf { it.isNotEmpty() && it.length <= 40 }
    }

    private fun codePointAt(text: String, index: Int): Int {
        val high = text[index]
        if (high.isHighSurrogate() && index + 1 < text.length) {
            val low = text[index + 1]
            if (low.isLowSurrogate()) {
                return ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000
            }
        }
        return high.code
    }
}
