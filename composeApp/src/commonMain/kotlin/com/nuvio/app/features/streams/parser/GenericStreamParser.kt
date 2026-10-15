package com.nuvio.app.features.streams.parser

import com.nuvio.app.features.streams.StreamDebridCacheState
import com.nuvio.app.features.streams.StreamItem

/**
 * Extracts [StreamParserInfo] from a single stream without assuming any addon format.
 *
 * Fields of the stream are read in order of reliability (structured metadata, filename,
 * bingeGroup, description, title, name). Single-value data (resolution, source, codec,
 * size) comes from the first field that contains it; lists (visual tags, audio, channels,
 * languages) are merged across all fields of the same stream and de-duplicated.
 */
object GenericStreamParser {
    private const val BYTES_PER_KIB = 1024.0

    /**
     * `addonId` prefixes of plugin streams (see `toPluginProviderGroups`): one group per
     * scraper (`plugin:<id>`) or one per repository when grouping is on (`plugin-repo:<url>`).
     */
    private val PLUGIN_ADDON_ID_PREFIXES = listOf("plugin:", "plugin-repo:")

    private class Field(
        val prepared: String,
        val allowLanguageWords: Boolean,
    )

    fun parse(stream: StreamItem, knownTitle: String? = null): StreamParserInfo {
        val preparedTitle = knownTitle?.let(StreamParserText::prepare)?.replace('\n', ' ')?.trim()
        val fields = buildFields(stream, preparedTitle)

        val rawStatusText = listOfNotNull(stream.name, stream.description, stream.title)
            .joinToString("\n")
        val rawStatusLower = rawStatusText.lowercase()

        val isCached = detectCached(stream, rawStatusLower)
        return StreamParserInfo(
            resolution = detectResolution(fields),
            tags = detectTags(fields),
            languages = detectLanguages(stream, fields),
            sizeBytes = detectSize(stream),
            durationSeconds = detectDuration(rawStatusText),
            sourceLabel = detectSourceLabel(stream, rawStatusText),
            isCached = isCached,
            isTorrent = !isCached && (stream.isTorrentStream || StreamParserDictionaries.p2p.containsMatchIn(rawStatusLower)),
            isProxied = !StreamParserDictionaries.proxyNegative.containsMatchIn(rawStatusLower) &&
                StreamParserDictionaries.proxyPositive.containsMatchIn(rawStatusLower),
        )
    }

    private fun buildFields(stream: StreamItem, preparedTitle: String?): List<Field> {
        val resolve = stream.clientResolve
        val raw = resolve?.stream?.raw
        val parsed = raw?.parsed
        val structured = parsed?.let {
            listOfNotNull(
                it.resolution,
                it.quality,
                it.codec,
                it.bitDepth,
                it.hdr.joinToString(" "),
                it.audio.joinToString(" "),
                it.channels.joinToString(" "),
                it.languages.joinToString(" "),
            ).joinToString(" ")
        }

        fun field(text: String?, allowLanguageWords: Boolean, stripTitle: Boolean = true): Field? {
            if (text.isNullOrBlank()) return null
            val prepared = StreamParserText.prepare(text)
                .lines()
                .map { line -> if (stripTitle) StreamParserText.stripTitle(line, preparedTitle) else line }
                .filter { it.isNotBlank() }
                .joinToString("\n")
            return prepared.takeIf { it.isNotBlank() }?.let { Field(it, allowLanguageWords) }
        }

        return listOfNotNull(
            field(structured, allowLanguageWords = true, stripTitle = false),
            field(stream.behaviorHints.filename, allowLanguageWords = true),
            field(raw?.filename, allowLanguageWords = true),
            field(resolve?.filename, allowLanguageWords = true),
            field(stream.debridCacheStatus?.cachedName, allowLanguageWords = true),
            field(raw?.torrentName, allowLanguageWords = true),
            field(resolve?.torrentName, allowLanguageWords = true),
            field(stream.behaviorHints.bingeGroup?.replace('|', '\n'), allowLanguageWords = false),
            field(stream.description, allowLanguageWords = true),
            field(stream.title?.takeIf { it != stream.description }, allowLanguageWords = true),
            field(stream.name, allowLanguageWords = false),
        )
    }

    private fun detectResolution(fields: List<Field>): StreamParserResolution {
        for (field in fields) {
            StreamParserDictionaries.resolutions
                .firstOrNull { it.regex.containsMatchIn(field.prepared) }
                ?.let { return it.value }
        }
        if (fields.any { StreamParserDictionaries.bareHd.containsMatchIn(it.prepared) }) {
            return StreamParserResolution.HD
        }
        // Missing or unrecognised resolution: default to SD.
        return StreamParserResolution.SD
    }

    private fun detectTags(fields: List<Field>): List<StreamParserTag> {
        val source = firstMatch(fields, StreamParserDictionaries.sources)
        val codec = firstMatch(fields, StreamParserDictionaries.codecs)
        val visual = unionMatches(fields, StreamParserDictionaries.visualTags)
        val audio = unionMatches(fields, StreamParserDictionaries.audioTags)
        val channels = fields
            .flatMap { field ->
                StreamParserDictionaries.channels.findAll(field.prepared).map { "${it.groupValues[1]}.${it.groupValues[2]}" }
            }
            .distinct()
            .sortedByDescending { it.toDoubleOrNull() ?: 0.0 }
            .map { StreamParserTag(StreamParserTagKind.CHANNELS, it) }

        val all = listOfNotNull(source) + visual + listOfNotNull(codec) + audio + channels
        val absorbed = all.flatMap { StreamParserDictionaries.absorbedBy[it.label].orEmpty() }.toSet()
        return all.filterNot { it.label in absorbed }
    }

    private fun firstMatch(
        fields: List<Field>,
        aliases: List<StreamParserDictionaries.Alias<StreamParserTag>>,
    ): StreamParserTag? {
        for (field in fields) {
            aliases.firstOrNull { it.regex.containsMatchIn(field.prepared) }?.let { return it.value }
        }
        return null
    }

    /** Union across fields, kept in dictionary order so chips always appear in the same sequence. */
    private fun unionMatches(
        fields: List<Field>,
        aliases: List<StreamParserDictionaries.Alias<StreamParserTag>>,
    ): List<StreamParserTag> =
        aliases
            .filter { alias -> fields.any { alias.regex.containsMatchIn(it.prepared) } }
            .map { it.value }

    private fun detectLanguages(stream: StreamItem, fields: List<Field>): List<StreamParserLanguage> {
        val languages = linkedSetOf<StreamParserLanguage>()
        listOfNotNull(stream.name, stream.description, stream.title, stream.behaviorHints.filename)
            .flatMap(StreamParserText::audioFlagCountries)
            .mapNotNullTo(languages) { StreamParserDictionaries.flagLanguages[it] }

        fields.filter { it.allowLanguageWords }.forEach { field ->
            field.prepared.lines().forEach { line -> collectLanguageWords(line, languages) }
        }

        if (languages.size > 1) languages.remove(StreamParserLanguage.MULTI)
        return languages.toList()
    }

    private fun collectLanguageWords(line: String, into: MutableSet<StreamParserLanguage>) {
        var inSubtitleList = false
        for (token in StreamParserText.tokens(line)) {
            when {
                token in StreamParserDictionaries.subtitleMarkers -> inSubtitleList = true
                StreamParserDictionaries.fusedSubtitlePrefix.matches(token) -> inSubtitleList = true
                token in StreamParserDictionaries.languageWords -> {
                    if (!inSubtitleList) into += StreamParserDictionaries.languageWords.getValue(token)
                }
                token in StreamParserDictionaries.languageListGlue -> Unit
                else -> inSubtitleList = false
            }
        }
    }

    private fun detectSize(stream: StreamItem): Long? {
        stream.behaviorHints.videoSize?.takeIf { it > 0 }?.let { return it }
        stream.debridCacheStatus?.cachedSize?.takeIf { it > 0 }?.let { return it }
        stream.clientResolve?.stream?.raw?.size?.takeIf { it > 0 }?.let { return it }

        val texts = listOfNotNull(stream.description, stream.title, stream.name)
        for (text in texts) {
            val match = StreamParserDictionaries.size.find(text) ?: continue
            val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            val multiplier = when (match.groupValues[2].lowercase()) {
                "tb", "tib" -> BYTES_PER_KIB * BYTES_PER_KIB * BYTES_PER_KIB * BYTES_PER_KIB
                "gb", "gib" -> BYTES_PER_KIB * BYTES_PER_KIB * BYTES_PER_KIB
                else -> BYTES_PER_KIB * BYTES_PER_KIB
            }
            val bytes = (value * multiplier).toLong()
            if (bytes > 0) return bytes
        }
        return null
    }

    private fun detectDuration(rawText: String): Long? {
        StreamParserDictionaries.durationHms.find(rawText)?.let { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLongOrNull() ?: 0L
            val seconds = match.groupValues[3].toLongOrNull() ?: 0L
            val total = hours * 3600 + minutes * 60 + seconds
            if (total > 0) return total
        }
        StreamParserDictionaries.durationMinutes.find(rawText)?.let { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: return null
            if (minutes in 1..600) return minutes * 60
        }
        return null
    }

    private fun detectSourceLabel(stream: StreamItem, rawText: String): String? {
        for (marker in StreamParserDictionaries.sourceMarkers) {
            val captured = marker.find(rawText)?.groupValues?.getOrNull(1) ?: continue
            StreamParserText.cleanLabel(captured)?.let { return it }
        }
        return stream.sourceName?.let(StreamParserText::cleanLabel)
            ?: StreamParserText.cleanLabel(stream.addonName)
    }

    private fun detectCached(stream: StreamItem, rawLower: String): Boolean {
        // Plugin streams are scraped direct links: they can never be debrid-cached.
        if (PLUGIN_ADDON_ID_PREFIXES.any { stream.addonId.startsWith(it) }) return false
        when (stream.debridCacheStatus?.state) {
            StreamDebridCacheState.CACHED -> return true
            StreamDebridCacheState.NOT_CACHED -> return false
            else -> Unit
        }
        if (stream.clientResolve?.isCached == true) return true
        if (StreamParserDictionaries.cachedNegative.containsMatchIn(rawLower)) return false
        return StreamParserDictionaries.cachedPositive.containsMatchIn(rawLower)
    }
}
