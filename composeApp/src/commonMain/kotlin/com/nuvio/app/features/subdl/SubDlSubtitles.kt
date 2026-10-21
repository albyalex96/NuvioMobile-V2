package com.nuvio.app.features.subdl

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.opensubtitles.subtitleExtensionForText
import com.nuvio.app.features.player.AddonSubtitle
import com.nuvio.app.features.player.AddonSubtitleSource
import com.nuvio.app.features.player.DeviceLanguagePreferences
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.normalizeLanguageCode
import com.nuvio.app.features.player.resolvePreferredSubtitleLanguageTargets
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * SubDL files are listed with a placeholder URL (`subdl://file/<archive path>`, plus
 * `?episode=S:E` for episodes): every archive download counts against the daily limit, so it is
 * downloaded only when the file is attached to the player (see [SubDlLinkResolver]).
 */
internal const val SUBDL_URL_PREFIX = "subdl://file/"
private const val EPISODE_PARAMETER = "?episode="

internal fun isSubDlPlaceholderUrl(url: String?): Boolean =
    url?.startsWith(SUBDL_URL_PREFIX) == true

/** Archive path and wanted episode of a placeholder. */
internal data class SubDlPlaceholder(
    val archivePath: String,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val url: String
        get() = buildString {
            append(SUBDL_URL_PREFIX)
            append(archivePath.trimStart('/'))
            if (season != null && episode != null) append("$EPISODE_PARAMETER$season:$episode")
        }

    /** File cache name, without extension. */
    val cacheName: String
        get() = buildString {
            append(archivePath.substringAfterLast('/').substringBeforeLast('.').filter { it.isLetterOrDigit() || it == '-' || it == '_' })
            if (season != null && episode != null) append("_s${season}e$episode")
        }
}

internal fun parseSubDlPlaceholder(url: String): SubDlPlaceholder? {
    if (!isSubDlPlaceholderUrl(url)) return null
    val body = url.removePrefix(SUBDL_URL_PREFIX)
    val path = body.substringBefore(EPISODE_PARAMETER)
    if (path.isBlank()) return null
    val episodePart = body.substringAfter(EPISODE_PARAMETER, missingDelimiterValue = "")
    val season = episodePart.substringBefore(':').toIntOrNull()
    val episode = episodePart.substringAfter(':', missingDelimiterValue = "").toIntOrNull()
    return SubDlPlaceholder(
        archivePath = "/$path",
        season = season.takeIf { episode != null },
        episode = episode.takeIf { season != null },
    )
}

/** Codes SubDL knows (`BR_PT` = Brazilian Portuguese, `ZH_BG` = Traditional Chinese). */
private val subDlLanguageCodes = setOf(
    "AR", "DA", "NL", "EN", "FA", "FI", "FR", "ID", "IT", "NO", "RO", "ES", "SV", "VI", "SQ", "AZ",
    "BE", "BN", "BS", "BG", "MY", "CA", "ZH", "HR", "CS", "EO", "ET", "KA", "DE", "EL", "KL", "HE",
    "HI", "HU", "IS", "JA", "KO", "KU", "LV", "LT", "MK", "MS", "ML", "PL", "PT", "RU", "SR", "SI",
    "SK", "SL", "TL", "TA", "TE", "TH", "TR", "UK", "UR", "HY", "KK", "KY", "KM", "KN", "MN", "EU",
    "GL", "GA", "JV", "SU", "BR_PT", "ZH_BG",
)

/** App language code (`AvailableLanguageOptions`) to SubDL code, null when SubDL lacks it. */
internal fun toSubDlLanguage(code: String): String? {
    val normalized = normalizeLanguageCode(code) ?: return null
    val subDl = when (normalized) {
        "none", "forced", "device", "default", "original" -> return null
        "pt-br" -> "BR_PT"
        "zh-tw", "zh-hant", "zh-hk" -> "ZH_BG"
        "nb", "nn", "no" -> "NO"
        "iw" -> "HE"
        "fil" -> "TL"
        // Device locales carry a region (it-IT, en-US) that SubDL does not use.
        else -> normalized.substringBefore('-').uppercase()
    }
    return subDl.takeIf { it in subDlLanguageCodes }
}

/** SubDL language code (or name) to the app code used by the subtitle panel and the filters. */
internal fun fromSubDlLanguage(code: String?, name: String? = null): String {
    val raw = code?.trim()?.uppercase().orEmpty()
    return when {
        raw == "BR_PT" -> "pt-br"
        raw == "ZH_BG" -> "zh-tw"
        raw.isNotEmpty() -> normalizeLanguageCode(raw.lowercase()) ?: raw.lowercase()
        !name.isNullOrBlank() -> normalizeLanguageCode(name.trim().lowercase()) ?: name.trim().lowercase()
        else -> "unknown"
    }
}

/** Parses Stremio video ids: `tt123`, `tt123:1:2`, `tmdb:123`, `tmdb:123:1:2`. */
internal fun subDlQueryFor(type: String, videoId: String): SubDlQuery? {
    val parts = videoId.trim().split(':')
    val (imdbId, tmdbId, rest) = when {
        parts.firstOrNull()?.startsWith("tt") == true -> Triple(parts.first(), null, parts.drop(1))
        parts.firstOrNull().equals("tmdb", ignoreCase = true) && parts.size >= 2 ->
            Triple(null, parts[1].toIntOrNull(), parts.drop(2))
        else -> return null
    }
    if (imdbId?.removePrefix("tt")?.toIntOrNull() == null && tmdbId == null) return null
    val isSeries = type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true)
    val season = rest.getOrNull(0)?.toIntOrNull()
    val episode = rest.getOrNull(1)?.toIntOrNull()
    if (isSeries && (season == null || episode == null)) return null
    return SubDlQuery(
        imdbId = imdbId,
        tmdbId = tmdbId,
        season = season.takeIf { isSeries },
        episode = episode.takeIf { isSeries },
    )
}

/** Languages to ask for: the chosen ones, or the player's preferred subtitle languages. */
internal fun subDlSearchLanguages(settings: SubDlSettings): List<String> {
    val chosen = settings.languages.ifEmpty {
        PlayerSettingsRepository.ensureLoaded()
        val player = PlayerSettingsRepository.uiState.value
        resolvePreferredSubtitleLanguageTargets(
            preferredSubtitleLanguage = player.preferredSubtitleLanguage,
            secondaryPreferredSubtitleLanguage = player.secondaryPreferredSubtitleLanguage,
            deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
        )
    }
    return chosen.mapNotNull(::toSubDlLanguage).distinct()
}

/** True when the item is a pack with more episodes than the requested one. */
internal fun SubDlSubtitleItem.isSeasonPack(query: SubDlQuery): Boolean {
    if (!query.isEpisode) return false
    if (fullSeason) return true
    val from = episodeFrom
    val end = episodeEnd
    return from != null && end != null && end > from
}

/** Keeps the items allowed by the settings and the requested episode. */
internal fun List<SubDlSubtitleItem>.filterFor(query: SubDlQuery, settings: SubDlSettings): List<SubDlSubtitleItem> =
    filter { item ->
        if (item.url.isNullOrBlank()) return@filter false
        if (!settings.includeHearingImpaired && item.hi) return@filter false
        if (!settings.includeAiTranslated && item.aiTranslated) return@filter false
        if (!query.isEpisode) return@filter true
        if (item.season != null && item.season != 0 && item.season != query.season) return@filter false
        if (item.isSeasonPack(query)) {
            if (!settings.includeSeasonPacks) return@filter false
            val from = item.episodeFrom
            val end = item.episodeEnd
            // A pack that states its range must contain the episode.
            return@filter from == null || end == null || query.episode!! in from..end
        }
        item.episode == null || item.episode == 0 || item.episode == query.episode
    }

/**
 * Strict check for the results of a whole-season search, where an item without an episode number
 * may be any episode: packs, items of the episode, or items whose name says `S05E08` / `5x08`.
 */
internal fun SubDlSubtitleItem.belongsToEpisode(query: SubDlQuery): Boolean {
    val season = query.season ?: return true
    val episode = query.episode ?: return true
    if (isSeasonPack(query)) return true
    if (this.episode != null && this.episode != 0) return this.episode == episode
    val names = listOfNotNull(releaseName, name).joinToString(" ")
    return Regex("(?i)s0*$season[ ._-]*e0*$episode(?!\\d)").containsMatchIn(names) ||
        Regex("(?i)(?<!\\d)0*${season}x0*$episode(?!\\d)").containsMatchIn(names)
}

internal fun SubDlSubtitleItem.toAddonSubtitle(query: SubDlQuery): AddonSubtitle? {
    val path = url?.takeIf { it.isNotBlank() } ?: return null
    val placeholder = SubDlPlaceholder(archivePath = path, season = query.season, episode = query.episode)
    val title = releaseName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: SUBDL_PROVIDER_NAME
    return AddonSubtitle(
        id = "subdl_${placeholder.cacheName}",
        url = placeholder.url,
        language = fromSubDlLanguage(language, lang),
        display = buildString {
            append(title)
            if (hi) append(" · SDH")
            if (aiTranslated) append(" · AI")
            if (isSeasonPack(query)) append(" · Pack")
        },
        addonName = SUBDL_PROVIDER_NAME,
        source = AddonSubtitleSource.SubDl,
    )
}

internal object SubDlSubtitleSource {
    private val log = Logger.withTag(SUBDL_LOG_TAG)

    /** True when a search would run for this content, used to count the loading progress. */
    fun isActiveFor(type: String, videoId: String): Boolean =
        SubDlRepository.snapshot().isUsable && subDlQueryFor(type, videoId) != null

    /** Searches the subtitles of [videoId]; never throws (an error means no SubDL results). */
    suspend fun fetch(type: String, videoId: String): List<AddonSubtitle> {
        val settings = SubDlRepository.snapshot()
        if (!settings.isUsable) return emptyList()
        val query = subDlQueryFor(type, videoId) ?: run {
            log.d { "Skipped: unsupported id $videoId ($type)" }
            return emptyList()
        }
        val languages = subDlSearchLanguages(settings)
        return try {
            val items = searchEachLanguage(settings, query, languages)
            val subtitles = items
                .filterFor(query, settings)
                // Single-episode files first: packs need the right file picked from the archive.
                .sortedBy { it.isSeasonPack(query) }
                .mapNotNull { it.toAddonSubtitle(query) }
                .distinctBy { it.url }
            log.i {
                "Found ${subtitles.size} subtitles for $videoId (${items.size} before filters, " +
                    "languages=${languages.ifEmpty { listOf("all") }}, " +
                    "by language=${subtitles.groupingBy { it.language }.eachCount()})"
            }
            subtitles
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.e(error) { "Search failed for $videoId" }
            emptyList()
        }
    }

    /**
     * One search per language, merged in preference order: SubDL returns at most 30 subtitles per
     * search, so asking several languages together lets the most common one (usually English) fill
     * the page and hide the preferred one. Fails only when every search fails.
     */
    private suspend fun searchEachLanguage(
        settings: SubDlSettings,
        query: SubDlQuery,
        languages: List<String>,
    ): List<SubDlSubtitleItem> {
        // null = no language filter (SubDL returns every language).
        val searches: List<String?> = languages.ifEmpty { listOf(null) }
        val results = coroutineScope {
            searches.map { language ->
                async { language to runCatching { searchLanguage(settings, query, language) } }
            }.awaitAll()
        }
        results.forEach { (language, result) ->
            result.exceptionOrNull()?.let { error ->
                if (error is CancellationException) throw error
                log.w(error) { "Search failed for language ${language ?: "all"}" }
            }
        }
        if (results.all { it.second.isFailure }) throw results.first().second.exceptionOrNull()!!
        return results.flatMap { it.second.getOrDefault(emptyList()) }
    }

    /**
     * Searches one language. For an episode with no usable result it also searches the whole
     * season: many subtitles (Italian ones above all) are only on SubDL as season packs, which the
     * episode search does not return.
     */
    private suspend fun searchLanguage(
        settings: SubDlSettings,
        query: SubDlQuery,
        language: String?,
    ): List<SubDlSubtitleItem> {
        val languages = listOfNotNull(language)
        val label = language ?: "all"
        val items = SubDlApi.search(settings.apiKey, query, languages)
        val usable = items.filterFor(query, settings).size
        log.d { "Language $label: ${items.size} subtitles, $usable usable" }
        if (!query.isEpisode || usable > 0) return items
        val seasonItems = try {
            SubDlApi.search(settings.apiKey, query.copy(episode = null), languages)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "Season search failed for language $label" }
            return items
        }
        log.d {
            "Language $label: season ${query.season} search gave ${seasonItems.size} subtitles, " +
                "${seasonItems.filterFor(query, settings).size} usable for episode ${query.episode}"
        }
        return items + seasonItems.filter { it.belongsToEpisode(query) }
    }
}

private val subtitleFileExtensions = listOf("srt", "vtt", "ass", "ssa", "sub")

private fun isSubtitleEntry(name: String): Boolean {
    val fileName = name.substringAfterLast('/')
    if (name.startsWith("__MACOSX/") || fileName.startsWith("._")) return false
    return fileName.substringAfterLast('.', "").lowercase() in subtitleFileExtensions
}

/**
 * Picks the subtitle of the wanted episode from an archive: an `S01E02` / `1x02` / `E02` match
 * first, otherwise the first subtitle file (single-file archives, or packs whose names are unclear).
 */
internal fun pickSubDlEntry(entries: List<ZipEntryData>, season: Int?, episode: Int?): ZipEntryData? {
    val subtitles = entries.filter { isSubtitleEntry(it.name) }.sortedBy { it.name.lowercase() }
    if (subtitles.size <= 1 || episode == null) return subtitles.firstOrNull()
    val seasonPattern = season?.let { "0*$it" } ?: "\\d+"
    val patterns = listOf(
        Regex("(?i)s$seasonPattern[ ._-]*e0*$episode(?!\\d)"),
        Regex("(?i)(?<!\\d)${seasonPattern}x0*$episode(?!\\d)"),
        Regex("(?i)(?:^|[^a-z])e(?:p|pisode)?[ ._-]*0*$episode(?!\\d)"),
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        subtitles.firstOrNull { pattern.containsMatchIn(it.name.substringAfterLast('/')) }
    } ?: subtitles.first()
}

/**
 * Subtitle text from the file bytes: UTF-8 (with or without BOM), UTF-16 with BOM, otherwise
 * Windows-1252, the usual encoding of the older SubDL files with accented letters.
 */
internal fun decodeSubtitleBytes(bytes: ByteArray): String {
    if (bytes.size >= 2) {
        val first = bytes[0].toInt() and 0xFF
        val second = bytes[1].toInt() and 0xFF
        if (first == 0xFF && second == 0xFE) return decodeUtf16(bytes, 2, littleEndian = true)
        if (first == 0xFE && second == 0xFF) return decodeUtf16(bytes, 2, littleEndian = false)
    }
    val utf8 = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
    if (utf8 != null) return utf8.removePrefix("﻿")
    return buildString(bytes.size) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            append(if (value in 0x80..0x9F) windows1252High[value - 0x80] else value.toChar())
        }
    }
}

private fun decodeUtf16(bytes: ByteArray, start: Int, littleEndian: Boolean): String = buildString {
    var index = start
    while (index + 1 < bytes.size) {
        val low = bytes[if (littleEndian) index else index + 1].toInt() and 0xFF
        val high = bytes[if (littleEndian) index + 1 else index].toInt() and 0xFF
        append(((high shl 8) or low).toChar())
        index += 2
    }
}

private val windows1252High = charArrayOf(
    '€', '\u0081', '‚', 'ƒ', '„', '…', '†', '‡',
    'ˆ', '‰', 'Š', '‹', 'Œ', '\u008D', 'Ž', '\u008F',
    '\u0090', '‘', '’', '“', '”', '•', '–', '—',
    '˜', '™', 'š', '›', 'œ', '\u009D', 'ž', 'Ÿ',
)

/**
 * Turns the placeholder URL into a playable local file: downloads the archive (one download of
 * the daily limit), picks the subtitle of the episode, decodes it and keeps it in the file cache
 * as UTF-8, so picking it again costs nothing, even after a restart.
 */
internal object SubDlLinkResolver {
    private const val CACHE_DIRECTORY = "subdl"
    private val extensions = listOf("srt", "vtt", "ass")

    private val log = Logger.withTag(SUBDL_LOG_TAG)
    private val mutex = Mutex()
    private val lock = SynchronizedObject()
    private val resolved = mutableMapOf<String, String>()

    private fun cachePath(placeholder: SubDlPlaceholder, extension: String) =
        "$CACHE_DIRECTORY/${placeholder.cacheName}.$extension"

    private fun cachedUri(placeholder: SubDlPlaceholder): String? {
        synchronized(lock) { resolved[placeholder.url] }?.let { return it }
        val extension = extensions.firstOrNull { AppFileCache.read(cachePath(placeholder, it)) != null } ?: return null
        val uri = AppFileCache.fileUri(cachePath(placeholder, extension)) ?: return null
        synchronized(lock) { resolved[placeholder.url] = uri }
        return uri
    }

    /** Returns [url] itself when it is not a SubDL placeholder, null when it cannot be resolved. */
    suspend fun resolve(url: String): String? {
        if (!isSubDlPlaceholderUrl(url)) return url
        val placeholder = parseSubDlPlaceholder(url) ?: return null
        return mutex.withLock {
            cachedUri(placeholder)?.let { cached ->
                log.d { "Using cached file ${placeholder.cacheName}" }
                return@withLock cached
            }
            try {
                val archive = SubDlApi.downloadArchive(placeholder.archivePath)
                val entries = readZipEntries(archive, accept = ::isSubtitleEntry)
                // Some files are served as plain text instead of a ZIP archive.
                val bytes = pickSubDlEntry(entries, placeholder.season, placeholder.episode)
                    ?.also { log.d { "Picked ${it.name} out of ${entries.size} subtitle files" } }
                    ?.bytes
                    ?: archive.takeIf { entries.isEmpty() && !it.isZipArchive() }
                if (bytes == null) {
                    log.w { "No subtitle file in archive ${placeholder.cacheName}" }
                    return@withLock null
                }
                val text = decodeSubtitleBytes(bytes)
                if (text.isBlank()) {
                    log.w { "Subtitle ${placeholder.cacheName} is empty" }
                    return@withLock null
                }
                val extension = subtitleExtensionForText(text)
                val path = cachePath(placeholder, extension)
                AppFileCache.write(path, text)
                val uri = AppFileCache.fileUri(path) ?: run {
                    log.w { "File cache unavailable, cannot store ${placeholder.cacheName}" }
                    return@withLock null
                }
                synchronized(lock) { resolved[placeholder.url] = uri }
                log.i { "Subtitle ${placeholder.cacheName} saved as .$extension (${text.length} chars)" }
                uri
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "Download failed for ${placeholder.cacheName}" }
                null
            }
        }
    }

    /** The subtitle text of a resolved placeholder, read from the file cache. */
    suspend fun resolveText(url: String): String? {
        val placeholder = parseSubDlPlaceholder(url) ?: return null
        resolve(url) ?: return null
        return extensions.firstNotNullOfOrNull { AppFileCache.read(cachePath(placeholder, it)) }
    }

    /** The placeholder URL a resolved file was obtained from, to map player callbacks back. */
    fun placeholderFor(uri: String): String? = synchronized(lock) {
        resolved.entries.firstOrNull { it.value == uri }?.key
    }

    /** Forgets the in-memory map; the cached files stay (they belong to the same archives). */
    fun clear() {
        synchronized(lock) { resolved.clear() }
    }
}

private fun ByteArray.isZipArchive(): Boolean =
    size >= 4 && this[0] == 'P'.code.toByte() && this[1] == 'K'.code.toByte()
