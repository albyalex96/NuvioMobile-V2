package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.features.anime.AnimeEpisodeTarget
import com.nuvio.app.features.anime.AnimeIdMapper
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingHistoryItem
import com.nuvio.app.features.tracking.TrackingHistoryWriter
import com.nuvio.app.features.tracking.TrackingListStatus
import com.nuvio.app.features.tracking.TrackingListWriter
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingMutationResolution
import com.nuvio.app.features.tracking.TrackingMutationResult
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleEvent
import com.nuvio.app.features.tracking.TrackingScrobbler
import com.nuvio.app.features.tracking.TrackingWatchedProvider
import com.nuvio.app.features.tracking.buildTrackingMediaReference
import com.nuvio.app.features.watched.WatchedItem
import kotlinx.coroutines.CancellationException

/** Percentage at which a finished playback counts as a watched episode (same as Simkl and MDBList). */
internal const val MAL_WATCHED_THRESHOLD_PERCENT = 80.0

/**
 * MAL write side. MAL keeps no playback position, only a counter of watched episodes, so:
 * - a scrobble STOP past [MAL_WATCHED_THRESHOLD_PERCENT] counts as the episode being watched;
 * - manual watched marks move the counter forward, unmarks move it back;
 * - list moves change the status of the anime.
 * MAL is never a watched-history source, so [pull] returns nothing.
 */
object MalTrackingWrites : TrackingScrobbler, TrackingWatchedProvider, TrackingListWriter, TrackingHistoryWriter {
    private val log = Logger.withTag("MalSync")
    override val providerId: TrackingProviderId = TrackingProviderId.MAL

    init {
        TrackingProviderRegistry.registerScrobbler(this)
        TrackingProviderRegistry.registerWatchedProvider(this)
        TrackingProviderRegistry.registerListWriter(this)
        TrackingProviderRegistry.registerHistoryWriter(this)
    }

    fun ensureRegistered() = Unit

    override suspend fun scrobble(
        profileId: Int,
        action: TrackingScrobbleAction,
        event: TrackingScrobbleEvent,
    ) {
        if (action != TrackingScrobbleAction.STOP || !isActive(profileId)) return
        if (event.progressPercent < MAL_WATCHED_THRESHOLD_PERCENT) {
            log.d { "MAL scrobble stop ignored at ${event.progressPercent.toInt()}%" }
            return
        }
        val target = resolve(event.media) ?: return
        MalListRepository.markEpisodeWatched(target.malId, target.episode)
    }

    override suspend fun pull(profileId: Int, pageSize: Int): List<WatchedItem> = emptyList()

    override suspend fun push(profileId: Int, items: Collection<WatchedItem>) {
        if (!isActive(profileId)) return
        latestEpisodes(items, pick = { episodes -> episodes.max() }).forEach { (malId, episode) ->
            MalListRepository.markEpisodeWatched(malId, episode)
        }
    }

    override suspend fun delete(profileId: Int, items: Collection<WatchedItem>) {
        if (!isActive(profileId)) return
        latestEpisodes(items, pick = { episodes -> episodes.min() }).forEach { (malId, episode) ->
            MalListRepository.markEpisodeUnwatched(malId, episode)
        }
    }

    override suspend fun moveToList(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
        destination: TrackingListStatus,
    ): TrackingMutationResult {
        if (!isActive(profileId)) return TrackingMutationResult(attemptedCount = 0)
        var notFound = 0
        val resolutions = mutableListOf<TrackingMutationResolution>()
        items.forEach { media ->
            val malId = MalLibraryResolver.malId(media)
            if (malId == null) {
                notFound += 1
                return@forEach
            }
            MalListRepository.setStatus(malId, MalListStatus.fromTracking(destination))
            resolutions += TrackingMutationResolution(listStatus = destination)
        }
        return TrackingMutationResult(attemptedCount = items.size, notFoundCount = notFound, resolutions = resolutions)
    }

    override suspend fun removeFromList(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
    ): TrackingMutationResult {
        if (!isActive(profileId)) return TrackingMutationResult(attemptedCount = 0)
        var notFound = 0
        items.forEach { media ->
            val malId = MalLibraryResolver.malId(media)
            if (malId == null) notFound += 1 else MalListRepository.remove(malId)
        }
        return TrackingMutationResult(attemptedCount = items.size, notFoundCount = notFound)
    }

    override suspend fun addToHistory(
        profileId: Int,
        items: Collection<TrackingHistoryItem>,
    ): TrackingMutationResult {
        if (!isActive(profileId)) return TrackingMutationResult(attemptedCount = 0)
        var notFound = 0
        items.forEach { item ->
            val target = resolve(item.media)
            if (target == null) notFound += 1 else MalListRepository.markEpisodeWatched(target.malId, target.episode)
        }
        return TrackingMutationResult(attemptedCount = items.size, notFoundCount = notFound)
    }

    override suspend fun removeFromHistory(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
    ): TrackingMutationResult {
        if (!isActive(profileId)) return TrackingMutationResult(attemptedCount = 0)
        var notFound = 0
        items.forEach { media ->
            val target = resolve(media)
            if (target == null) notFound += 1 else MalListRepository.markEpisodeUnwatched(target.malId, target.episode)
        }
        return TrackingMutationResult(attemptedCount = items.size, notFoundCount = notFound)
    }

    /** One (MAL id, episode) per anime entry: several episodes of the same cour become one update. */
    private suspend fun latestEpisodes(
        items: Collection<WatchedItem>,
        pick: (List<Int>) -> Int,
    ): List<Pair<Long, Int>> {
        val episodesByMal = linkedMapOf<Long, MutableList<Int>>()
        items.forEach { item ->
            if (item.episode == null && !item.type.equals("movie", ignoreCase = true)) {
                log.d { "MAL ignores the whole-series watched mark for ${item.id}" }
                return@forEach
            }
            val media = buildTrackingMediaReference(
                contentType = item.type,
                parentMetaId = item.id,
                videoId = item.videoId,
                title = item.name,
                releaseInfo = item.releaseInfo,
                seasonNumber = item.season,
                episodeNumber = item.episode,
            )
            val target = resolve(media) ?: return@forEach
            episodesByMal.getOrPut(target.malId, ::mutableListOf) += target.episode
        }
        return episodesByMal.map { (malId, episodes) -> malId to pick(episodes) }
    }

    private suspend fun resolve(media: TrackingMediaReference): ResolvedMalEpisode? {
        val target: AnimeEpisodeTarget = try {
            AnimeIdMapper.resolve(media)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "MAL episode resolution failed for ${media.stableKey}" }
            null
        } ?: run {
            log.d { "No MAL entry for ${media.stableKey}" }
            return null
        }
        val malId = target.mapping.mal ?: run {
            log.d { "Anime ${media.stableKey} has no MAL id" }
            return null
        }
        return ResolvedMalEpisode(malId, target.episode)
    }

    private fun isActive(profileId: Int): Boolean =
        profileId == ProfileRepository.activeProfileId && MalAuthRepository.isAuthenticated.value

    private data class ResolvedMalEpisode(val malId: Long, val episode: Int)
}
