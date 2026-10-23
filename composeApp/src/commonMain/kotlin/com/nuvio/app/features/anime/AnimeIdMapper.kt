package com.nuvio.app.features.anime

import co.touchlab.kermit.Logger
import com.nuvio.app.core.storage.AppFileCache
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingMediaReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * Shared anime ID index used by every anime tracker (MyAnimeList today, AniList and Kitsu later).
 *
 * The two public lists are downloaded once, reduced to [AnimeIdMapping] entries and cached in the
 * app file cache; the cache is refreshed in the background once a week.
 */
object AnimeIdMapper {
    private val log = Logger.withTag("AnimeIdMapper")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val _indexState = MutableStateFlow<AnimeIdIndex?>(null)

    /** The in-memory index, observable so views re-project once the lists are loaded. */
    val indexState: StateFlow<AnimeIdIndex?> = _indexState.asStateFlow()

    private var index: AnimeIdIndex?
        get() = _indexState.value
        set(value) { _indexState.value = value }

    @Volatile
    private var fetchedAtEpochMs: Long = 0L

    @Volatile
    private var lastFailureEpochMs: Long = 0L

    @Volatile
    private var refreshInFlight = false

    /** The index if it is already in memory; never blocks or downloads. */
    fun currentIndex(): AnimeIdIndex? = index

    /** The index, loading it from the cache or the network on first use; null when unavailable. */
    suspend fun index(): AnimeIdIndex? {
        index?.let { current ->
            if (isStale(fetchedAtEpochMs)) refreshInBackground()
            return current
        }
        return loadMutex.withLock {
            index ?: loadFromCache() ?: download()
        }
    }

    suspend fun resolve(media: TrackingMediaReference): AnimeEpisodeTarget? {
        val target = index()?.resolve(media) ?: media.ids.mal?.let { mal ->
            AnimeEpisodeTarget(AnimeIdMapping(mal = mal), media.episode?.number?.coerceAtLeast(1) ?: 1)
        }
        log.d {
            "resolve ${media.stableKey} s=${media.episode?.season} e=${media.episode?.number} -> " +
                "mal=${target?.mapping?.mal} episode=${target?.episode}"
        }
        return target
    }

    suspend fun isKnownAnime(ids: TrackingExternalIds): Boolean =
        ids.mal != null || ids.kitsu != null || ids.anilist != null || ids.anidb != null ||
            index()?.isKnownAnime(ids) == true

    fun ensureLoadedAsync() {
        if (index != null) return
        scope.launch { index() }
    }

    private fun refreshInBackground() {
        if (refreshInFlight) return
        refreshInFlight = true
        scope.launch {
            try {
                loadMutex.withLock { download() }
            } finally {
                refreshInFlight = false
            }
        }
    }

    private fun loadFromCache(): AnimeIdIndex? {
        val payload = runCatching { AppFileCache.read(CACHE_PATH) }.getOrNull()?.takeIf(String::isNotBlank)
            ?: return null
        val cache = runCatching { json.decodeFromString<AnimeIdMappingCache>(payload) }
            .onFailure { error -> log.w(error) { "Anime ID cache is unreadable, downloading again" } }
            .getOrNull()
            ?.takeIf { it.entries.isNotEmpty() }
            ?: return null
        val loaded = AnimeIdIndex(cache.entries)
        index = loaded
        fetchedAtEpochMs = cache.fetchedAtEpochMs
        log.i { "Anime ID index loaded from cache (${loaded.size} entries)" }
        if (isStale(cache.fetchedAtEpochMs)) refreshInBackground()
        return loaded
    }

    private suspend fun download(): AnimeIdIndex? {
        val now = nowEpochMs()
        if (now - lastFailureEpochMs < RETRY_AFTER_FAILURE_MS) return index
        return try {
            log.i { "Downloading the anime ID lists" }
            val fribb = fetch(FRIBB_ANIME_LIST_URL) ?: error("Fribb anime list unavailable")
            val kometa = fetch(KOMETA_ANIME_IDS_URL)
            if (kometa == null) log.w { "Kometa anime IDs unavailable, episode offsets will be missing" }
            val entries = parseAnimeIdMappings(fribb, kometa)
            check(entries.isNotEmpty()) { "Anime ID lists produced no entries" }
            val loaded = AnimeIdIndex(entries)
            index = loaded
            fetchedAtEpochMs = now
            runCatching {
                AppFileCache.write(CACHE_PATH, json.encodeToString(AnimeIdMappingCache(now, entries)))
            }.onFailure { error -> log.w(error) { "Failed to cache the anime ID index" } }
            log.i { "Anime ID index downloaded (${entries.size} entries)" }
            loaded
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            lastFailureEpochMs = now
            log.e(error) { "Failed to download the anime ID lists" }
            index
        }
    }

    private suspend fun fetch(url: String): String? {
        val response = httpRequestRaw(
            method = "GET",
            url = url,
            headers = mapOf("Accept" to "application/json"),
            body = "",
            maxResponseBodyBytes = MAX_LIST_BYTES,
        )
        if (response.status !in 200..299) {
            log.w { "Anime ID list HTTP ${response.status}: $url" }
            return null
        }
        return response.body.takeIf(String::isNotBlank)
    }

    private fun isStale(fetchedAt: Long): Boolean = nowEpochMs() - fetchedAt > REFRESH_INTERVAL_MS

    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    private const val CACHE_PATH = "anime/anime_id_index_v1.json"
    private const val MAX_LIST_BYTES = 24 * 1024 * 1024
    private const val REFRESH_INTERVAL_MS = 7L * 24L * 60L * 60L * 1_000L
    private const val RETRY_AFTER_FAILURE_MS = 30L * 60L * 1_000L
}
