package com.nuvio.app.features.player.skip

import co.touchlab.kermit.Logger
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.settings.IntegrationLogo
import kotlinx.coroutines.CancellationException

private val log = Logger.withTag("TimestampIntegrations")

internal object IntroDbTimestampProvider : TimestampProvider, TimestampSubmitter {
    override val id = "introdb"
    override val displayName = "IntroDB"
    override val logo = IntegrationLogo.IntroDb
    override val submitter: TimestampSubmitter = this

    override fun isLookupActive(): Boolean = IntroDbConfig.URL.isNotBlank()

    override suspend fun episodeIntervals(query: TimestampEpisodeQuery): List<SkipInterval> {
        val ref = query.imdbEpisode() ?: return emptyList()
        val data = SkipIntroApi.getIntroDbSegments(ref.imdbId, ref.season, ref.episode) ?: return emptyList()
        return listOfNotNull(
            data.intro.toSkipIntervalOrNull("intro"),
            data.recap.toSkipIntervalOrNull("recap"),
            data.outro.toSkipIntervalOrNull("outro"),
        )
    }

    override suspend fun movieIntervals(query: TimestampMovieQuery): List<SkipInterval> =
        SkipIntroApi.getIntroDbMovieSegments(query.imdbId)?.movieSkipIntervals().orEmpty()

    override fun isReady(): Boolean {
        val settings = PlayerSettingsRepository.uiState.value
        return IntroDbConfig.URL.isNotBlank() && settings.introSubmitEnabled && settings.introDbApiKey.isNotBlank()
    }

    override suspend fun submit(submission: TimestampSubmission): Boolean {
        val apiKey = PlayerSettingsRepository.uiState.value.introDbApiKey.trim()
        if (!isReady() || apiKey.isBlank()) return false
        val request = SubmitIntroRequest(
            imdbId = submission.imdbId,
            season = submission.season,
            episode = submission.episode,
            startSec = submission.startSec,
            endSec = submission.endSec,
            startMs = (submission.startSec * 1000).toLong(),
            endMs = (submission.endSec * 1000).toLong(),
            segmentType = submission.segmentType,
        )
        return SkipIntroApi.submitIntro(apiKey, request)
    }

    private fun IntroDbSegment?.toSkipIntervalOrNull(type: String): SkipInterval? {
        if (this == null) return null
        val start = startSec ?: startMs?.let { it / 1000.0 }
        val end = endSec ?: endMs?.let { it / 1000.0 }
        if (start == null || end == null || end <= start) return null
        return SkipInterval(startTime = start, endTime = end, type = type, provider = id)
    }
}

/**
 * SkipDB: open reads, API key only for submissions. With "send duration" on, the stream length is
 * passed so SkipDB can match or shift the timestamps, which makes results depend on the stream:
 * they are cached here by duration instead of per episode by the repository.
 */
internal object SkipDbTimestampProvider : TimestampProvider, TimestampSubmitter {
    override val id = "skipdb"
    override val displayName = "SkipDB"
    override val logo = IntegrationLogo.SkipDb
    override val submitter: TimestampSubmitter = this

    private val responseCache = HashMap<String, SkipDbSegmentsResponse>()

    private val settings: SkipDbSettings
        get() = TimestampProviderSettingsRepository.current().skipDb

    override fun isLookupActive(): Boolean = true

    override fun isCacheable(): Boolean = false

    override suspend fun episodeIntervals(query: TimestampEpisodeQuery): List<SkipInterval> {
        val ref = query.imdbEpisode() ?: return emptyList()
        val settings = settings
        val duration = if (settings.sendDuration) query.durationSec() else null
        val response = fetch(ref.imdbId, ref.season, ref.episode, duration, settings.adjustMode) ?: return emptyList()
        return response.toSkipIntervals(settings, duration, isMovie = false)
    }

    override suspend fun movieIntervals(query: TimestampMovieQuery): List<SkipInterval> {
        val settings = settings
        val duration = if (settings.sendDuration) query.durationSec() else null
        val response = fetch(query.imdbId, null, null, duration, settings.adjustMode) ?: return emptyList()
        return response.toSkipIntervals(settings, duration, isMovie = true)
    }

    private suspend fun fetch(
        imdbId: String,
        season: Int?,
        episode: Int?,
        durationSec: Double?,
        adjust: SkipDbAdjustMode,
    ): SkipDbSegmentsResponse? {
        val key = skipDbSegmentsUrl(imdbId, season, episode, durationSec, adjust)
        responseCache[key]?.let { return it }
        log.d { "SkipDB lookup $imdbId S${season ?: "-"}E${episode ?: "-"} duration=${durationSec?.toLong() ?: "none"} adjust=${adjust.apiValue}" }
        val response = SkipIntroApi.getSkipDbSegments(imdbId, season, episode, durationSec, adjust)
        if (response == null) {
            log.w { "SkipDB lookup failed for $imdbId S${season ?: "-"}E${episode ?: "-"}" }
            return null
        }
        responseCache[key] = response
        return response
    }

    override fun isReady(): Boolean = settings.submitEnabled && settings.apiKey.isNotBlank()

    override suspend fun submit(submission: TimestampSubmission): Boolean {
        val settings = settings
        if (!isReady()) return false
        val request = SkipDbSubmitRequest(
            imdbId = submission.imdbId,
            segmentType = submission.segmentType,
            season = submission.season,
            episode = submission.episode,
            startMs = (submission.startSec * 1000).toLong(),
            endMs = (submission.endSec * 1000).toLong(),
            durationMs = submission.durationSec?.takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() },
        )
        val result = SkipIntroApi.submitSkipDbSegment(settings.apiKey, request)
        log.i { "SkipDB submission: HTTP ${result?.httpStatus ?: "failed"}, status ${result?.status ?: "-"}" }
        if (result != null) responseCache.clear()
        return result?.isAccepted == true
    }

    suspend fun verifyApiKey(apiKey: String): Boolean = SkipIntroApi.verifySkipDbApiKey(apiKey)

    override fun clearCache() {
        responseCache.clear()
    }
}

internal object AnimeSkipTimestampProvider : TimestampProvider {
    override val id = "animeskip"
    override val displayName = "AnimeSkip"
    override val logo = IntegrationLogo.AnimeSkip

    private val showIdCache = HashMap<String, String>()
    private const val NO_ID = "__none__"

    override fun isLookupActive(): Boolean {
        val settings = PlayerSettingsRepository.uiState.value
        return settings.animeSkipEnabled && settings.animeSkipClientId.isNotBlank()
    }

    override suspend fun episodeIntervals(query: TimestampEpisodeQuery): List<SkipInterval> {
        val anime = query.animeEpisode() ?: return emptyList()
        val anilistId = anime.anilistId ?: return emptyList()
        val clientId = PlayerSettingsRepository.uiState.value.animeSkipClientId.trim()
        if (clientId.isBlank()) return emptyList()

        return try {
            val showIds = resolveShowIds(anilistId, clientId)
            for (showId in showIds) {
                val graphql = "{ findEpisodesByShowId(showId: \"$showId\") { season number timestamps { at type { name } } } }"
                val response = SkipIntroApi.queryAnimeSkip(clientId, graphql) ?: continue
                val episodes = response.data?.findEpisodesByShowId ?: continue
                val targetEpisode = episodes.firstOrNull { it.number?.toIntOrNull() == anime.episode } ?: continue

                val sorted = (targetEpisode.timestamps ?: continue).sortedBy { it.at }
                val result = sorted.mapIndexedNotNull { i, ts ->
                    val endTime = sorted.getOrNull(i + 1)?.at ?: Double.MAX_VALUE
                    val type = when (ts.type.name.lowercase()) {
                        "intro", "new intro" -> "op"
                        "credits" -> "ed"
                        "recap" -> "recap"
                        else -> return@mapIndexedNotNull null
                    }
                    SkipInterval(startTime = ts.at, endTime = endTime, type = type, provider = id)
                }
                if (result.isNotEmpty()) return result
            }
            emptyList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveShowIds(anilistId: String, clientId: String): List<String> {
        showIdCache[anilistId]?.let { cached ->
            return if (cached == NO_ID) emptyList() else listOf(cached)
        }
        val graphql = "{ findShowsByExternalId(service: ANILIST, serviceId: \"$anilistId\") { id } }"
        val showIds = try {
            SkipIntroApi.queryAnimeSkip(clientId, graphql)
                ?.data?.findShowsByExternalId?.map { it.id } ?: emptyList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }

        if (showIds.size == 1) showIdCache[anilistId] = showIds[0]
        else if (showIds.isEmpty()) showIdCache[anilistId] = NO_ID
        return showIds
    }

    override fun clearCache() {
        showIdCache.clear()
    }
}

/**
 * AniSkip: anime only (keyed by MAL ID), no key. With "send duration" on, the episode length is passed
 * so AniSkip prefers timestamps recorded on a release of similar length; results then depend on the
 * stream and are cached here by URL instead of per episode by the repository.
 */
internal object AniSkipTimestampProvider : TimestampProvider {
    override val id = "aniskip"
    override val displayName = "AniSkip"
    override val logo = IntegrationLogo.AniSkip

    private val responseCache = HashMap<String, AniSkipResponse>()

    private val settings: AniSkipSettings
        get() = TimestampProviderSettingsRepository.current().aniSkip

    override fun isLookupActive(): Boolean = settings.enabled

    override fun isCacheable(): Boolean = !settings.sendDuration

    override suspend fun episodeIntervals(query: TimestampEpisodeQuery): List<SkipInterval> {
        val anime = query.animeEpisode() ?: return emptyList()
        val malId = anime.malId ?: return emptyList()
        val duration = if (settings.sendDuration) query.durationSec() else null
        val key = aniSkipTimesUrl(malId, anime.episode, duration)
        val response = responseCache[key]
            ?: SkipIntroApi.getAniSkipTimes(malId, anime.episode, duration)?.also { responseCache[key] = it }
            ?: run {
                log.w { "AniSkip lookup failed for MAL $malId episode ${anime.episode}" }
                return emptyList()
            }
        log.d { "AniSkip MAL $malId episode ${anime.episode} length=${duration?.toLong() ?: "none"}: found=${response.found}" }
        if (!response.found) return emptyList()
        return response.results?.map { result ->
            SkipInterval(
                startTime = result.interval.startTime,
                endTime = result.interval.endTime,
                type = result.skipType,
                provider = id,
            )
        }.orEmpty()
    }

    override fun clearCache() {
        responseCache.clear()
    }
}
