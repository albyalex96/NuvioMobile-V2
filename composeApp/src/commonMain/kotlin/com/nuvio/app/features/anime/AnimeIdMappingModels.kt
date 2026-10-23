package com.nuvio.app.features.anime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One anime entry (one MAL/AniList/Kitsu entry, usually a single season or cour) with the IDs of
 * the catalogs Nuvio uses and its position in the TVDB/TMDB numbering of the parent show.
 *
 * [tvdbEpisodeOffset] is how many TVDB episodes of [tvdbSeason] come before this entry, so TVDB
 * episode `E` is episode `E - offset` of the entry. A [tvdbSeason] of 0 means specials.
 */
@Serializable
data class AnimeIdMapping(
    @SerialName("mal") val mal: Long? = null,
    @SerialName("anilist") val anilist: Long? = null,
    @SerialName("kitsu") val kitsu: Long? = null,
    @SerialName("anidb") val anidb: Long? = null,
    @SerialName("simkl") val simkl: Long? = null,
    @SerialName("imdb") val imdb: List<String> = emptyList(),
    @SerialName("tmdb_tv") val tmdbTv: Long? = null,
    @SerialName("tmdb_movie") val tmdbMovie: Long? = null,
    @SerialName("tvdb") val tvdb: Long? = null,
    @SerialName("tvdb_season") val tvdbSeason: Int? = null,
    @SerialName("tmdb_season") val tmdbSeason: Int? = null,
    @SerialName("tvdb_offset") val tvdbEpisodeOffset: Int = 0,
    @SerialName("type") val type: String? = null,
) {
    val isMovie: Boolean
        get() = type.equals("MOVIE", ignoreCase = true)
}

/** The anime entry an episode (or a movie) belongs to, with its episode number inside that entry. */
data class AnimeEpisodeTarget(
    val mapping: AnimeIdMapping,
    /** Episode number inside the entry; 1 for movies. */
    val episode: Int,
)

@Serializable
internal data class AnimeIdMappingCache(
    @SerialName("fetched_at") val fetchedAtEpochMs: Long,
    @SerialName("entries") val entries: List<AnimeIdMapping>,
)
