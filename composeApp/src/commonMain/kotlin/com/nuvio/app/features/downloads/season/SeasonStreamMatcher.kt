package com.nuvio.app.features.downloads.season

import com.nuvio.app.features.streams.FilenameEpisodeMatcher
import com.nuvio.app.features.streams.StreamDebridCacheState
import com.nuvio.app.features.streams.StreamItem

/**
 * Finds, among the streams of another episode, the one that matches the stream picked for the
 * first episode. Only the same source is accepted (the user chose it on purpose); within it the
 * expected file name wins, then the binge group, then the closest quality.
 */
internal object SeasonStreamMatcher {

    fun templateOf(stream: StreamItem, seasonNumber: Int, episodeNumber: Int): SeasonStreamTemplate {
        val text = stream.qualityText()
        return SeasonStreamTemplate(
            addonId = stream.addonId,
            addonName = stream.addonName,
            bingeGroup = stream.behaviorHints.bingeGroup?.takeIf { it.isNotBlank() },
            filename = stream.fileName(),
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            resolution = resolutionOf(text),
            qualityTokens = qualityTokensOf(text),
        )
    }

    fun select(
        template: SeasonStreamTemplate,
        seasonNumber: Int,
        episodeNumber: Int,
        streams: List<StreamItem>,
        isDownloadable: (StreamItem) -> Boolean,
    ): StreamItem? = rank(template, seasonNumber, episodeNumber, streams, emptySet(), isDownloadable).firstOrNull()

    /**
     * Streams to try for an episode, best first: those of the chosen source, then, as a fallback,
     * those of the other sources with the closest quality. Streams in [excludedKeys] (already
     * failed for this episode) and streams that name another episode are left out.
     */
    fun rank(
        template: SeasonStreamTemplate,
        seasonNumber: Int,
        episodeNumber: Int,
        streams: List<StreamItem>,
        excludedKeys: Set<String>,
        isDownloadable: (StreamItem) -> Boolean,
    ): List<StreamItem> {
        val candidates = streams
            .filter { it.debridCacheStatus?.state != StreamDebridCacheState.NOT_CACHED }
            .filterNot { it.isForAnotherEpisode(seasonNumber, episodeNumber) }
            .filterNot { keyOf(it) in excludedKeys }
            .filter(isDownloadable)
        if (candidates.isEmpty()) return emptyList()

        val expectedFilename = template.filename?.let { filename ->
            FilenameEpisodeMatcher.buildNextFilename(
                filename = filename,
                currentSeason = template.seasonNumber,
                currentEpisode = template.episodeNumber,
                nextSeason = seasonNumber,
                nextEpisode = episodeNumber,
            )
        }
        val (sameSource, otherSources) = candidates.withIndex().partition { isFromSource(it.value, template) }
        // Stable: on equal scores the order of the sources (their own ranking) decides.
        val ordering = compareByDescending<Pair<IndexedValue<StreamItem>, Int>> { it.second }.thenBy { it.first.index }
        return sameSource.map { it to score(it.value, template, expectedFilename) }.sortedWith(ordering).map { it.first.value } +
            otherSources.map { it to qualityScore(it.value, template) }.sortedWith(ordering).map { it.first.value }
    }

    fun isFromSource(stream: StreamItem, template: SeasonStreamTemplate): Boolean =
        stream.addonId == template.addonId || stream.addonName == template.addonName

    /** Identity of a stream across lookups (its links can change, e.g. debrid or signed URLs). */
    fun keyOf(stream: StreamItem): String = listOf(
        stream.addonId,
        stream.fileName() ?: stream.infoHash?.let { "$it:${stream.fileIdx ?: ""}" } ?: stream.playableDirectUrl.orEmpty(),
        stream.name.orEmpty(),
        stream.title.orEmpty(),
    ).joinToString("|")

    private fun score(stream: StreamItem, template: SeasonStreamTemplate, expectedFilename: String?): Int {
        var score = 0
        if (FilenameEpisodeMatcher.matches(stream.fileName(), expectedFilename)) score += 1_000
        if (template.bingeGroup != null && stream.behaviorHints.bingeGroup == template.bingeGroup) score += 500
        if (stream.addonId == template.addonId) score += 50
        return score + qualityScore(stream, template)
    }

    private fun qualityScore(stream: StreamItem, template: SeasonStreamTemplate): Int {
        var score = 0
        val text = stream.qualityText()
        val resolution = resolutionOf(text)
        if (template.resolution != null && resolution != null) {
            score += if (resolution == template.resolution) 60 else -60
        }
        score += qualityTokensOf(text).count { it in template.qualityTokens } * 10
        return score
    }

    /** A file name that names another episode (season packs list the file of each episode). */
    private fun StreamItem.isForAnotherEpisode(seasonNumber: Int, episodeNumber: Int): Boolean {
        val filename = fileName() ?: return false
        val (season, episode) = FilenameEpisodeMatcher.extractSeasonEpisode(filename) ?: return false
        return episode != episodeNumber || (season != null && season != seasonNumber)
    }

    private fun StreamItem.fileName(): String? =
        (behaviorHints.filename ?: clientResolve?.filename ?: debridCacheStatus?.cachedName)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun StreamItem.qualityText(): String =
        listOfNotNull(name, title, description, fileName()).joinToString(" ")

    internal fun resolutionOf(text: String): String? {
        val lower = text.lowercase()
        return when {
            Regex("""\b(2160p|4k|uhd)\b""").containsMatchIn(lower) -> "2160p"
            Regex("""\b1440p\b""").containsMatchIn(lower) -> "1440p"
            Regex("""\b(1080p|fhd)\b""").containsMatchIn(lower) -> "1080p"
            Regex("""\b720p\b""").containsMatchIn(lower) -> "720p"
            Regex("""\b(576p|480p|sd)\b""").containsMatchIn(lower) -> "480p"
            else -> null
        }
    }

    internal fun qualityTokensOf(text: String): List<String> {
        val lower = text.lowercase()
        return QUALITY_TOKENS.filter { (_, regex) -> regex.containsMatchIn(lower) }.map { it.first }
    }

    private val QUALITY_TOKENS = listOf(
        "hdr" to Regex("""\bhdr(10\+?)?\b"""),
        "dv" to Regex("""\b(dv|dovi|dolby ?vision)\b"""),
        "hevc" to Regex("""\b(hevc|x265|h\.?265)\b"""),
        "avc" to Regex("""\b(avc|x264|h\.?264)\b"""),
        "av1" to Regex("""\bav1\b"""),
        "remux" to Regex("""\bremux\b"""),
        "bluray" to Regex("""\b(blu-?ray|bdrip|brrip)\b"""),
        "web-dl" to Regex("""\bweb-?dl\b"""),
        "webrip" to Regex("""\bweb-?rip\b"""),
        "atmos" to Regex("""\batmos\b"""),
    )
}
