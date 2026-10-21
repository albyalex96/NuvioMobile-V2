package com.nuvio.app.features.opensubtitles

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.AppFileCache
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
 * OpenSubtitles files are listed with a placeholder URL (`opensubtitles://file/<file_id>`): the
 * real link costs one download of the daily quota, so it is asked only when the file is attached
 * to the player (see [OpenSubtitlesLinkResolver]).
 */
internal const val OPENSUBTITLES_URL_PREFIX = "opensubtitles://file/"

internal fun isOpenSubtitlesPlaceholderUrl(url: String?): Boolean =
    url?.startsWith(OPENSUBTITLES_URL_PREFIX) == true

private fun openSubtitlesFileId(url: String): Long? =
    url.removePrefix(OPENSUBTITLES_URL_PREFIX).toLongOrNull()

/** App language code (`AvailableLanguageOptions`) to OpenSubtitles code, lowercase. */
internal fun toOpenSubtitlesLanguage(code: String): String? {
    val normalized = normalizeLanguageCode(code) ?: return null
    return when (normalized) {
        "none", "forced", "device", "default", "original" -> null
        "pt", "pt-pt" -> "pt-pt"
        "pt-br" -> "pt-br"
        "zh", "zh-cn", "zh-hans", "zh-sg" -> "zh-cn"
        "zh-tw", "zh-hant", "zh-hk" -> "zh-tw"
        "es-419" -> "ea"
        // Device locales carry a region (it-IT, en-US) that OpenSubtitles does not know.
        else -> normalized.substringBefore('-').takeIf { it.length in 2..3 }
    }
}

/** OpenSubtitles language code to the app code used by the subtitle panel and the filters. */
internal fun fromOpenSubtitlesLanguage(code: String?): String {
    val raw = code?.trim()?.lowercase().orEmpty()
    return when (raw) {
        "" -> "unknown"
        "ea" -> "es-419"
        "pt-pt" -> "pt"
        "ze" -> "zh"
        else -> normalizeLanguageCode(raw) ?: raw
    }
}

/** Parses Stremio video ids: `tt123`, `tt123:1:2`, `tmdb:123`, `tmdb:123:1:2`. */
internal fun openSubtitlesQueryFor(type: String, videoId: String): OpenSubtitlesQuery? {
    val parts = videoId.trim().split(':')
    val (idPart, rest) = when {
        parts.firstOrNull()?.startsWith("tt") == true -> parts.first() to parts.drop(1)
        parts.firstOrNull().equals("tmdb", ignoreCase = true) && parts.size >= 2 -> "tmdb:${parts[1]}" to parts.drop(2)
        else -> return null
    }
    val imdbId = idPart.takeIf { it.startsWith("tt") }?.removePrefix("tt")?.toIntOrNull()
    val tmdbId = idPart.takeIf { it.startsWith("tmdb:") }?.removePrefix("tmdb:")?.toIntOrNull()
    if (imdbId == null && tmdbId == null) return null
    val isSeries = type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true)
    val season = rest.getOrNull(0)?.toIntOrNull()
    val episode = rest.getOrNull(1)?.toIntOrNull()
    if (isSeries && (season == null || episode == null)) return null
    return OpenSubtitlesQuery(
        imdbId = imdbId,
        tmdbId = tmdbId,
        season = season.takeIf { isSeries },
        episode = episode.takeIf { isSeries },
    )
}

/** Languages to ask for: the chosen ones, or the player's preferred subtitle languages. */
internal fun openSubtitlesSearchLanguages(settings: OpenSubtitlesSettings): List<String> {
    val chosen = settings.languages.ifEmpty {
        PlayerSettingsRepository.ensureLoaded()
        val player = PlayerSettingsRepository.uiState.value
        resolvePreferredSubtitleLanguageTargets(
            preferredSubtitleLanguage = player.preferredSubtitleLanguage,
            secondaryPreferredSubtitleLanguage = player.secondaryPreferredSubtitleLanguage,
            deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
        )
    }
    return chosen.mapNotNull(::toOpenSubtitlesLanguage).distinct()
}

internal fun OpenSubtitlesSearchItem.toAddonSubtitles(): List<AddonSubtitle> {
    val language = fromOpenSubtitlesLanguage(attributes.language)
    return attributes.files.map { file ->
        val name = attributes.release?.takeIf { it.isNotBlank() }
            ?: file.fileName?.takeIf { it.isNotBlank() }
            ?: OPENSUBTITLES_PROVIDER_NAME
        AddonSubtitle(
            id = "opensubtitles_${file.fileId}",
            url = "$OPENSUBTITLES_URL_PREFIX${file.fileId}",
            language = language,
            display = buildString {
                append(name)
                if (attributes.hearingImpaired) append(" · SDH")
                if (attributes.aiTranslated || attributes.machineTranslated) append(" · AI")
            },
            addonName = OPENSUBTITLES_PROVIDER_NAME,
            source = AddonSubtitleSource.OpenSubtitles,
        )
    }
}

internal object OpenSubtitlesSubtitleSource {
    private val log = Logger.withTag(OPENSUBTITLES_LOG_TAG)

    /** True when a search would run for this content, used to count the loading progress. */
    fun isActiveFor(type: String, videoId: String): Boolean =
        OpenSubtitlesRepository.snapshot().isUsable && openSubtitlesQueryFor(type, videoId) != null

    /** Searches the subtitles of [videoId]; never throws (an error means no OpenSubtitles results). */
    suspend fun fetch(type: String, videoId: String): List<AddonSubtitle> {
        val settings = OpenSubtitlesRepository.snapshot()
        if (!settings.isUsable) return emptyList()
        val query = openSubtitlesQueryFor(type, videoId) ?: run {
            log.d { "Skipped: unsupported id $videoId ($type)" }
            return emptyList()
        }
        val languages = openSubtitlesSearchLanguages(settings)
        return try {
            val subtitles = searchEachLanguage(settings, query, languages)
                .flatMap { items ->
                    items.sortedWith(
                        compareByDescending<OpenSubtitlesSearchItem> { it.attributes.fromTrusted }
                            .thenByDescending { it.attributes.downloadCount },
                    )
                }
                .flatMap { it.toAddonSubtitles() }
                .distinctBy { it.url }
            log.i { "Found ${subtitles.size} subtitles for $videoId (languages=${languages.ifEmpty { listOf("all") }})" }
            subtitles
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.e(error) { "Search failed for $videoId" }
            emptyList()
        }
    }

    /**
     * One search per language, in preference order (each list sorted on its own): OpenSubtitles
     * returns one page of results, so asking several languages together lets the most downloaded
     * one (usually English) fill the page and hide the preferred one. Fails only when every
     * search fails.
     */
    private suspend fun searchEachLanguage(
        settings: OpenSubtitlesSettings,
        query: OpenSubtitlesQuery,
        languages: List<String>,
    ): List<List<OpenSubtitlesSearchItem>> {
        if (languages.size <= 1) return listOf(OpenSubtitlesApi.search(settings.apiKey, query, languages, settings))
        val results = coroutineScope {
            languages.map { language ->
                async {
                    language to runCatching { OpenSubtitlesApi.search(settings.apiKey, query, listOf(language), settings) }
                }
            }.awaitAll()
        }
        results.forEach { (language, result) ->
            result.exceptionOrNull()?.let { error ->
                if (error is CancellationException) throw error
                log.w(error) { "Search failed for language $language" }
            }
            result.getOrNull()?.let { log.d { "Language $language: ${it.size} results" } }
        }
        if (results.all { it.second.isFailure }) throw results.first().second.exceptionOrNull()!!
        return results.map { it.second.getOrDefault(emptyList()) }
    }
}

/** File extension matching the subtitle text, whatever the link says. */
internal fun subtitleExtensionForText(text: String): String {
    val head = text.replace("\uFEFF", "").trimStart().take(4_000)
    return when {
        head.startsWith("WEBVTT", ignoreCase = true) -> "vtt"
        head.startsWith("[Script Info]", ignoreCase = true) ||
            head.contains("[V4+ Styles]", ignoreCase = true) ||
            head.contains("[V4 Styles]", ignoreCase = true) -> "ass"
        else -> "srt"
    }
}

/**
 * Turns the placeholder URL into a playable local file: asks the download link (one download of
 * the daily quota), downloads the text with plain headers (the player's subtitle client sends
 * `Accept: application/json`, which the OpenSubtitles file server does not answer with the file),
 * names it by its real format and keeps it in the file cache, so picking it again costs nothing.
 */
internal object OpenSubtitlesLinkResolver {
    private const val CACHE_DIRECTORY = "opensubtitles"
    private val extensions = listOf("srt", "vtt", "ass")

    private val log = Logger.withTag(OPENSUBTITLES_LOG_TAG)
    private val mutex = Mutex()
    private val lock = SynchronizedObject()
    private val resolved = mutableMapOf<Long, String>()

    private fun cachePath(fileId: Long, extension: String) = "$CACHE_DIRECTORY/$fileId.$extension"

    /** Already downloaded file of [fileId], from this session or an earlier one. */
    private fun cachedUri(fileId: Long): String? {
        synchronized(lock) { resolved[fileId] }?.let { return it }
        val extension = extensions.firstOrNull { AppFileCache.read(cachePath(fileId, it)) != null } ?: return null
        val uri = AppFileCache.fileUri(cachePath(fileId, extension)) ?: return null
        synchronized(lock) { resolved[fileId] = uri }
        return uri
    }

    /** Returns [url] itself when it is not an OpenSubtitles placeholder, null when it cannot be resolved. */
    suspend fun resolve(url: String): String? {
        if (!isOpenSubtitlesPlaceholderUrl(url)) return url
        val fileId = openSubtitlesFileId(url) ?: return null
        return mutex.withLock {
            cachedUri(fileId)?.let { cached ->
                log.d { "Using cached file $fileId" }
                return@withLock cached
            }
            val apiKey = OpenSubtitlesRepository.snapshot().apiKey
            if (apiKey.isBlank()) {
                log.w { "Cannot download file $fileId: no API key" }
                return@withLock null
            }
            try {
                val response = OpenSubtitlesApi.downloadLink(apiKey, fileId)
                val link = response.link?.takeIf { it.isNotBlank() } ?: run {
                    log.w { "No download link for file $fileId: ${response.message}" }
                    return@withLock null
                }
                log.i { "Download link ready for file $fileId (remaining downloads today: ${response.remaining ?: "?"})" }
                val text = OpenSubtitlesApi.downloadFile(link)
                if (text.isBlank()) {
                    log.w { "Downloaded file $fileId is empty" }
                    return@withLock null
                }
                val extension = subtitleExtensionForText(text)
                val path = cachePath(fileId, extension)
                AppFileCache.write(path, text)
                val uri = AppFileCache.fileUri(path) ?: run {
                    log.w { "File cache unavailable, cannot store file $fileId" }
                    return@withLock null
                }
                synchronized(lock) { resolved[fileId] = uri }
                log.i { "File $fileId saved as .$extension (${text.length} chars)" }
                uri
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "Download failed for file $fileId" }
                null
            }
        }
    }

    /** The subtitle text of a resolved placeholder, read from the file cache. */
    suspend fun resolveText(url: String): String? {
        val fileId = openSubtitlesFileId(url) ?: return null
        resolve(url) ?: return null
        return extensions.firstNotNullOfOrNull { AppFileCache.read(cachePath(fileId, it)) }
    }

    /** The placeholder URL a resolved file was obtained from, to map player callbacks back. */
    fun placeholderFor(uri: String): String? = synchronized(lock) {
        resolved.entries.firstOrNull { it.value == uri }?.let { "$OPENSUBTITLES_URL_PREFIX${it.key}" }
    }

    /** Forgets the in-memory map; the cached files stay (they belong to the same file ids). */
    fun clear() {
        synchronized(lock) { resolved.clear() }
    }
}
