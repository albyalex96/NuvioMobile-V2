package com.nuvio.app.features.anime

import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference

/** In-memory lookups over [AnimeIdMapping] entries. Pure, so the matching rules are unit tested. */
class AnimeIdIndex(val entries: List<AnimeIdMapping>) {
    private val byMal = entries.filter { it.mal != null }.associateBy { it.mal!! }
    private val byAnilist = entries.filter { it.anilist != null }.associateBy { it.anilist!! }
    private val byKitsu = entries.filter { it.kitsu != null }.associateBy { it.kitsu!! }
    private val byAnidb = entries.filter { it.anidb != null }.associateBy { it.anidb!! }
    private val byTvdb = entries.filter { it.tvdb != null }.groupBy { it.tvdb!! }
    private val byTmdbTv = entries.filter { it.tmdbTv != null }.groupBy { it.tmdbTv!! }
    private val byTmdbMovie = entries.filter { it.tmdbMovie != null }.groupBy { it.tmdbMovie!! }
    private val byImdb: Map<String, List<AnimeIdMapping>> =
        entries
            .flatMap { entry -> entry.imdb.map { id -> id.lowercase() to entry } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

    val size: Int get() = entries.size

    fun byMal(id: Long): AnimeIdMapping? = byMal[id]
    fun byAnilist(id: Long): AnimeIdMapping? = byAnilist[id]
    fun byKitsu(id: Long): AnimeIdMapping? = byKitsu[id]

    /** The entry named directly by a tracker ID in [ids], if any. */
    fun direct(ids: TrackingExternalIds): AnimeIdMapping? =
        ids.mal?.let { byMal[it] ?: AnimeIdMapping(mal = it) }
            ?: ids.kitsu?.let(byKitsu::get)
            ?: ids.anilist?.let(byAnilist::get)
            ?: ids.anidb?.let(byAnidb::get)

    /** Every entry of the show or movie identified by catalog IDs (IMDB, TMDB, TVDB). */
    fun catalogEntries(ids: TrackingExternalIds): List<AnimeIdMapping> {
        ids.tvdb?.trim()?.toLongOrNull()?.let(byTvdb::get)?.takeIf(List<AnimeIdMapping>::isNotEmpty)?.let { return it }
        ids.imdb?.trim()?.lowercase()?.let(byImdb::get)?.takeIf(List<AnimeIdMapping>::isNotEmpty)?.let { return it }
        ids.tmdb?.let { tmdb -> (byTmdbTv[tmdb].orEmpty() + byTmdbMovie[tmdb].orEmpty()) }
            ?.takeIf(List<AnimeIdMapping>::isNotEmpty)?.let { return it }
        return emptyList()
    }

    fun isKnownAnime(ids: TrackingExternalIds): Boolean =
        ids.mal != null || ids.kitsu != null || ids.anilist != null || ids.anidb != null ||
            catalogEntries(ids).isNotEmpty()

    /**
     * Finds the anime entry and the episode number inside it for [media].
     *
     * Tracker IDs (`mal:`, `kitsu:` ...) already name one entry and their episode numbers are
     * per entry. Catalog IDs name a whole show: the season picks the entries, then the TVDB
     * episode offset picks the cour (the entry with the largest offset still below the episode).
     */
    fun resolve(media: TrackingMediaReference): AnimeEpisodeTarget? {
        val episode = media.episode
        direct(media.ids)?.let { mapping ->
            return AnimeEpisodeTarget(mapping = mapping, episode = episode?.number?.coerceAtLeast(1) ?: 1)
        }
        val candidates = catalogEntries(media.ids)
        if (candidates.isEmpty()) return null

        if (episode == null || media.kind == TrackingMediaKind.MOVIE) {
            val movie = candidates.firstOrNull(AnimeIdMapping::isMovie)
                ?: candidates.singleOrNull()
                ?: return null
            return AnimeEpisodeTarget(mapping = movie, episode = 1)
        }

        val season = episode.season ?: 1
        val number = episode.number
        val series = candidates.filterNot(AnimeIdMapping::isMovie)
        val usesTmdbOnly = media.ids.tvdb.isNullOrBlank() && media.ids.imdb.isNullOrBlank()
        val inSeason = series.filter { entry ->
            (if (usesTmdbOnly) entry.tmdbSeason ?: entry.tvdbSeason else entry.tvdbSeason) == season
        }
        inSeason
            .filter { entry -> number > entry.tvdbEpisodeOffset }
            .maxByOrNull(AnimeIdMapping::tvdbEpisodeOffset)
            ?.let { entry -> return AnimeEpisodeTarget(entry, number - entry.tvdbEpisodeOffset) }

        // A catalog ID that belongs to a single entry (common for per-season IMDB IDs).
        val single = series.singleOrNull() ?: return null
        return if (season <= 1 || single.tvdbSeason == null) AnimeEpisodeTarget(single, number) else null
    }

    /** First-season (or only) entry of a show, used when no episode is involved (library actions). */
    fun primaryEntry(ids: TrackingExternalIds): AnimeIdMapping? {
        direct(ids)?.let { return it }
        val candidates = catalogEntries(ids)
        return candidates.firstOrNull(AnimeIdMapping::isMovie).takeIf { candidates.all(AnimeIdMapping::isMovie) }
            ?: candidates
                .filterNot(AnimeIdMapping::isMovie)
                .filter { it.tvdbSeason == 1 || it.tvdbSeason == null }
                .minByOrNull(AnimeIdMapping::tvdbEpisodeOffset)
            ?: candidates.firstOrNull()
    }
}
