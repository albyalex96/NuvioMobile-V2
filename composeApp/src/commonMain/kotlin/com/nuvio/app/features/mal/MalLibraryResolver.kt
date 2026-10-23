package com.nuvio.app.features.mal

import com.nuvio.app.features.anime.AnimeIdIndex
import com.nuvio.app.features.anime.AnimeIdMapper
import com.nuvio.app.features.anime.AnimeIdMapping
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.simkl.SimklAnimeIdPreference
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.tracking.parseTrackingExternalIds

/** Translates between MAL anime IDs and the content IDs used by Nuvio catalogs and the library. */
internal object MalLibraryResolver {
    /** The MAL anime a show or movie (no episode involved) refers to: its first season for shows. */
    suspend fun malId(media: TrackingMediaReference): Long? =
        media.ids.mal ?: AnimeIdMapper.index()?.primaryEntry(media.ids)?.mal

    suspend fun malId(item: LibraryItem): Long? = malId(item.externalIds())

    suspend fun malId(ids: TrackingExternalIds): Long? =
        ids.mal ?: AnimeIdMapper.index()?.primaryEntry(ids)?.mal

    /** Same as [malId] without loading the index; null until the index is in memory. */
    fun malIdIfReady(ids: TrackingExternalIds): Long? =
        ids.mal ?: AnimeIdMapper.currentIndex()?.primaryEntry(ids)?.mal

    /**
     * Content ID of a MAL entry in the library, following the anime ID preference shared with
     * Simkl: IMDB/TVDB group seasons under the show, MAL/Kitsu keep one item per season.
     */
    fun contentId(
        malId: Long,
        index: AnimeIdIndex?,
        preference: SimklAnimeIdPreference = TrackingSettingsRepository.uiState.value.simklAnimeIdPreference,
    ): String {
        val mapping = index?.byMal(malId)
        val fallback = "mal:$malId"
        return when (preference) {
            SimklAnimeIdPreference.MAL -> fallback
            SimklAnimeIdPreference.KITSU -> mapping?.kitsu?.let { "kitsu:$it" } ?: fallback
            SimklAnimeIdPreference.TVDB -> mapping?.tvdb?.let { "tvdb:$it" } ?: fallback
            SimklAnimeIdPreference.IMDB -> mapping?.imdb?.firstOrNull()
                ?: mapping?.tvdb?.let { "tvdb:$it" }
                ?: fallback
        }
    }

    /** Every content ID a MAL entry can be reached with, for library lookups by catalog ID. */
    fun alternateContentIds(malId: Long, mapping: AnimeIdMapping?): Set<String> = buildSet {
        add("mal:$malId")
        mapping?.kitsu?.let { add("kitsu:$it") }
        mapping?.anilist?.let { add("anilist:$it") }
        mapping?.tvdb?.let { add("tvdb:$it") }
        mapping?.tmdbTv?.let { add("tmdb:$it") }
        mapping?.tmdbMovie?.let { add("tmdb:$it") }
        mapping?.imdb?.forEach { add(it.lowercase()) }
    }
}

internal fun LibraryItem.externalIds(): TrackingExternalIds =
    parseTrackingExternalIds(id).mergeMissing(
        TrackingExternalIds(
            imdb = imdbId,
            tmdb = tmdbId?.toLong(),
        ),
    )
