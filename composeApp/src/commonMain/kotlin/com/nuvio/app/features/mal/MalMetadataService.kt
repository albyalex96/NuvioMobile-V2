package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.features.anime.AnimeIdMapper
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaExternalRating
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_MAL
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.parseTrackingExternalIds
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException

/** Adds the MyAnimeList score to anime details when MAL is connected and MDBList did not provide it. */
object MalMetadataService {
    private val log = Logger.withTag("MalMetadata")
    private val cacheLock = SynchronizedObject()
    private val meanCache = mutableMapOf<Long, Double?>()

    suspend fun enrichMeta(meta: MetaDetails, fallbackItemId: String): MetaDetails {
        if (!MalAuthRepository.isAuthenticated.value) return meta
        if (meta.externalRatings.any { it.source == PROVIDER_MAL }) return meta
        val ids = parseTrackingExternalIds(meta.id)
            .mergeMissing(parseTrackingExternalIds(fallbackItemId))
            .mergeMissing(TrackingExternalIds(imdb = meta.imdbId))
        // Only the in-memory index: the details timeout must never cancel the first list download.
        val index = AnimeIdMapper.currentIndex()
        if (index == null) AnimeIdMapper.ensureLoadedAsync()
        val malId = ids.mal ?: index?.takeIf { it.isKnownAnime(ids) }?.primaryEntry(ids)?.mal ?: return meta
        val mean = try {
            meanFor(malId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "Failed to load the MAL score of $malId" }
            null
        } ?: return meta
        log.d { "MAL score $mean added to ${meta.id} (mal=$malId)" }
        return meta.copy(externalRatings = meta.externalRatings + MetaExternalRating(source = PROVIDER_MAL, value = mean))
    }

    private suspend fun meanFor(malId: Long): Double? {
        MalListRepository.entry(malId)?.mean?.let { return it }
        synchronized(cacheLock) {
            if (meanCache.containsKey(malId)) return meanCache[malId]
        }
        val mean = MalListRepository.fetchAnime(malId).mean?.takeIf { it in 0.0..10.0 }
        synchronized(cacheLock) { meanCache[malId] = mean }
        return mean
    }
}

/** External ratings are shown with MDBList active, or when the only rating is the MAL score. */
fun shouldShowExternalRatings(isMdbListActive: Boolean, ratings: List<MetaExternalRating>): Boolean =
    ratings.isNotEmpty() && (isMdbListActive || ratings.all { it.source == PROVIDER_MAL })
