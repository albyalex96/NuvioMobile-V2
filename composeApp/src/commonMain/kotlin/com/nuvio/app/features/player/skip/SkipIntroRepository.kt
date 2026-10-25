package com.nuvio.app.features.player.skip

import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Looks up skip timestamps on every active [TimestampProvider] in parallel and merges them by the
 * user's priority (see [mergeTimestampsByPriority]); also routes submissions to the chosen services.
 * A provider that fails or has no data contributes nothing: the lower ones fill the gaps.
 */
object SkipIntroRepository {

    private val log = Logger.withTag("TimestampIntegrations")
    private val cache = HashMap<String, List<SkipInterval>>()
    private val cacheMutex = Mutex()

    private val skipIntroEnabled: Boolean
        get() = PlayerSettingsRepository.uiState.value.skipIntroEnabled

    /**
     * @param durationSec length of the stream being played; may suspend until the player knows it.
     * Only providers that use it (SkipDB with "send duration") wait for it.
     */
    suspend fun getMovieSkipIntervals(
        contentId: String?,
        videoId: String?,
        requireSkipIntroEnabled: Boolean = true,
        durationSec: suspend () -> Double? = NoTimestampDuration,
    ): List<SkipInterval> {
        if (requireSkipIntroEnabled && !skipIntroEnabled) return emptyList()
        val imdbId = resolveMovieSkipImdbId(
            contentId, videoId,
            resolveTmdb = { TmdbService.tmdbToImdb(it, "movie") },
            resolveAnime = { source, id -> SimklIdResolver.resolveIds(source, id)?.imdb },
        ) ?: return emptyList()
        val query = TimestampMovieQuery(imdbId, durationSec)
        return trimMovieCreditsBeforePostCredits(
            queryProviders("movie:$imdbId") { it.movieIntervals(query) },
        )
    }

    suspend fun getSkipIntervals(
        imdbId: String?,
        season: Int,
        episode: Int,
        requireSkipIntroEnabled: Boolean = true,
        durationSec: suspend () -> Double? = NoTimestampDuration,
    ): List<SkipInterval> = coroutineScope {
        if (imdbId == null) return@coroutineScope emptyList()
        if (requireSkipIntroEnabled && !skipIntroEnabled) return@coroutineScope emptyList()

        val imdb = CompletableDeferred(ImdbEpisodeRef(imdbId, season, episode))
        // Resolve IMDB -> season-specific MAL/AniList via Simkl full_anime_seasons
        val anime = resolveAsync("anime IDs of $imdbId") {
            val simklIds = SimklIdResolver.resolveIdsForImdbEpisode(imdbId, season, episode)
                ?: return@resolveAsync null
            // Remap the TVDB episode number to the anime-entry-local episode number.
            val animeEpisode = SimklIdResolver.getEpisodeMapping(simklIds.simklId, simklIds.type)
                .firstOrNull { it.tvdbSeason == season && it.tvdbEpisode == episode }
                ?.animeEpisode
                ?: episode
            AnimeEpisodeRef(malId = simklIds.mal, anilistId = simklIds.anilist, episode = animeEpisode)
        }
        lookupEpisode("$imdbId:$season:$episode", imdb, anime, durationSec)
    }

    suspend fun getSkipIntervalsForMal(
        malId: String,
        episode: Int,
        requireSkipIntroEnabled: Boolean = true,
        imdbId: String? = null,
        imdbSeason: Int? = null,
        imdbEpisode: Int? = null,
        durationSec: suspend () -> Double? = NoTimestampDuration,
    ): List<SkipInterval> = coroutineScope {
        if (requireSkipIntroEnabled && !skipIntroEnabled) return@coroutineScope emptyList()

        val simklIds = resolveAsync("Simkl IDs of mal:$malId") { SimklIdResolver.resolveIds("mal", malId) }
        val anime = resolveAsync("anime IDs of mal:$malId") {
            AnimeEpisodeRef(malId = malId, anilistId = simklIds.await()?.anilist, episode = episode)
        }
        val imdb = resolveAsync("IMDB episode of mal:$malId") {
            resolveImdbEpisodeForAnime("mal", malId, episode, simklIds.await(), imdbId, imdbSeason, imdbEpisode)
        }
        lookupEpisode("mal:$malId:$episode", imdb, anime, durationSec)
    }

    suspend fun getSkipIntervalsForKitsu(
        kitsuId: String,
        episode: Int,
        requireSkipIntroEnabled: Boolean = true,
        imdbId: String? = null,
        imdbSeason: Int? = null,
        imdbEpisode: Int? = null,
        durationSec: suspend () -> Double? = NoTimestampDuration,
    ): List<SkipInterval> = coroutineScope {
        if (requireSkipIntroEnabled && !skipIntroEnabled) return@coroutineScope emptyList()

        val simklIds = resolveAsync("Simkl IDs of kitsu:$kitsuId") { SimklIdResolver.resolveIds("kitsu", kitsuId) }
        val anime = resolveAsync("anime IDs of kitsu:$kitsuId") {
            val ids = simklIds.await()
            AnimeEpisodeRef(malId = ids?.mal, anilistId = ids?.anilist, episode = episode)
        }
        val imdb = resolveAsync("IMDB episode of kitsu:$kitsuId") {
            resolveImdbEpisodeForAnime("kitsu", kitsuId, episode, simklIds.await(), imdbId, imdbSeason, imdbEpisode)
        }
        lookupEpisode("kitsu:$kitsuId:$episode", imdb, anime, durationSec)
    }

    /** A shared ID resolution: a failure only leaves the providers that need it without data. */
    private fun <T> CoroutineScope.resolveAsync(what: String, block: suspend () -> T?): Deferred<T?> = async {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.w(error) { "Could not resolve $what" }
            null
        }
    }

    /** IMDB numbering of an anime episode: the one given by the caller, or the Simkl TVDB mapping. */
    private suspend fun resolveImdbEpisodeForAnime(
        source: String,
        id: String,
        episode: Int,
        simklIds: SimklIdResolver.ResolvedIds?,
        imdbId: String?,
        imdbSeason: Int?,
        imdbEpisode: Int?,
    ): ImdbEpisodeRef? {
        val resolvedImdbId = imdbId ?: simklIds?.imdb ?: return null
        if (imdbSeason != null) return ImdbEpisodeRef(resolvedImdbId, imdbSeason, imdbEpisode ?: episode)
        if (simklIds == null) return null
        val tvdb = SimklIdResolver.resolveEpisodeTvdb(source, id, episode) ?: return null
        return ImdbEpisodeRef(resolvedImdbId, tvdb.first, imdbEpisode ?: tvdb.second)
    }

    private suspend fun lookupEpisode(
        key: String,
        imdb: Deferred<ImdbEpisodeRef?>,
        anime: Deferred<AnimeEpisodeRef?>,
        durationSec: suspend () -> Double?,
    ): List<SkipInterval> {
        val query = TimestampEpisodeQuery(key, imdb, anime, durationSec)
        return queryProviders(key) { it.episodeIntervals(query) }
    }

    private suspend fun queryProviders(
        key: String,
        fetch: suspend (TimestampProvider) -> List<SkipInterval>,
    ): List<SkipInterval> = coroutineScope {
        val providers = TimestampProviders.ordered().filter { it.isLookupActive() }
        val results = providers.map { provider ->
            async { fetchCached(provider, key, fetch) }
        }.awaitAll()
        val mergeProviders = TimestampProviderSettingsRepository.current().mergeProviders
        mergeTimestampsByPriority(results, mergeProviders).also { merged ->
            log.d {
                val found = providers.zip(results).joinToString { (provider, result) -> "${provider.id}=${result.size}" }
                "Timestamps for $key (merge=$mergeProviders): [$found] -> ${merged.joinToString { "${it.type}@${it.provider}" }}"
            }
        }
    }

    private suspend fun fetchCached(
        provider: TimestampProvider,
        key: String,
        fetch: suspend (TimestampProvider) -> List<SkipInterval>,
    ): List<SkipInterval> {
        val cacheKey = "${provider.id}|$key"
        val cacheable = provider.isCacheable()
        if (cacheable) cacheMutex.withLock { cache[cacheKey] }?.let { return it }
        val intervals = try {
            fetch(provider)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.w(error) { "${provider.displayName} lookup failed for $key" }
            return emptyList()
        }
        if (cacheable) cacheMutex.withLock { cache[cacheKey] = intervals }
        return intervals
    }

    /** Services the player's submit dialog sends to; empty when submission is not possible. */
    fun submitTargetNames(): List<String> = TimestampProviders.activeSubmitTargets().map { it.displayName }

    /**
     * Sends the timestamp to every chosen service and returns the result per service name.
     * The cached lookups are dropped so the next lookup can see the new data.
     */
    suspend fun submitIntro(
        imdbId: String,
        season: Int,
        episode: Int,
        startSec: Double,
        endSec: Double,
        segmentType: String,
        durationSec: Double? = null,
    ): Map<String, Boolean> = coroutineScope {
        val submission = TimestampSubmission(imdbId, season, episode, startSec, endSec, segmentType, durationSec)
        val targets = TimestampProviders.activeSubmitTargets()
        val results = targets.map { provider ->
            async {
                val accepted = try {
                    provider.submitter?.submit(submission) == true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    log.e(error) { "${provider.displayName} submission failed" }
                    false
                }
                provider.displayName to accepted
            }
        }.awaitAll().toMap()
        log.i { "Timestamp submission $segmentType for $imdbId S${season}E$episode: $results" }
        if (results.values.any { it }) clearLookupCache()
        results
    }

    suspend fun verifyIntroDbApiKey(apiKey: String): Boolean {
        return SkipIntroApi.verifyIntroDbApiKey(apiKey)
    }

    suspend fun verifySkipDbApiKey(apiKey: String): Boolean =
        SkipDbTimestampProvider.verifyApiKey(apiKey)

    private suspend fun clearLookupCache() {
        cacheMutex.withLock { cache.clear() }
        SkipDbTimestampProvider.clearCache()
    }

    fun clearCache() {
        cache.clear()
        TimestampProviders.all.forEach { it.clearCache() }
        SimklIdResolver.clearCache()
    }
}

internal suspend fun resolveMovieSkipImdbId(
    contentId: String?,
    videoId: String?,
    resolveTmdb: suspend (Int) -> String?,
    resolveAnime: suspend (String, String) -> String?,
): String? {
    val ids = listOfNotNull(contentId, videoId).map(String::trim).distinct()
    val imdbPattern = Regex("tt[0-9]+")
    ids.map { it.substringBefore(':') }.firstOrNull { imdbPattern.matches(it) }?.let { return it }
    return ids.firstNotNullOfOrNull { id ->
        val parts = id.split(':')
        val value = parts.getOrNull(1)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?: return@firstNotNullOfOrNull null
        when (parts.first().lowercase()) {
            "tmdb" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { resolveTmdb(it) }
            "mal", "kitsu" -> resolveAnime(parts.first().lowercase(), value)
            else -> null
        }?.trim()?.takeIf { imdbPattern.matches(it) }
    }
}
