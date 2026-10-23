package com.nuvio.app.features.anime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Fribb anime-lists: one array entry per anime with the IDs of most anime and media catalogs. */
internal const val FRIBB_ANIME_LIST_URL =
    "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-full.json"

/** Kometa Anime-IDs: AniDB-keyed TVDB season and episode offset of each entry. */
internal const val KOMETA_ANIME_IDS_URL =
    "https://raw.githubusercontent.com/Kometa-Team/Anime-IDs/master/anime_ids.json"

// Typed decoding (not a JsonElement tree) keeps the ~7 MB Fribb list cheap on low-memory TVs.
private val mappingJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

@Serializable
private data class FribbEntry(
    val type: String? = null,
    @SerialName("anidb_id") val anidb: Long? = null,
    @SerialName("anilist_id") val anilist: Long? = null,
    @SerialName("kitsu_id") val kitsu: Long? = null,
    @SerialName("mal_id") val mal: Long? = null,
    @SerialName("simkl_id") val simkl: Long? = null,
    /** An array of IMDB IDs (older dumps used a single string). */
    @SerialName("imdb_id") val imdb: JsonElement? = null,
    @SerialName("themoviedb_id") val tmdb: FribbTmdb? = null,
    @SerialName("tvdb_id") val tvdb: Long? = null,
    val season: FribbSeason? = null,
)

/** `movie` is an array of IDs in the current dumps, `tv` a number: both are read either way. */
@Serializable
private data class FribbTmdb(
    val tv: JsonElement? = null,
    val movie: JsonElement? = null,
)

@Serializable
private data class FribbSeason(
    val tvdb: Int? = null,
    val tmdb: Int? = null,
)

@Serializable
private data class KometaEntry(
    @SerialName("tvdb_id") val tvdb: Long? = null,
    @SerialName("tvdb_season") val season: Int? = null,
    @SerialName("tvdb_epoffset") val offset: Int? = null,
)

/**
 * Builds the compact index from the two public lists. Fribb gives the IDs; Kometa, joined by AniDB
 * ID, gives the TVDB season and episode offset (Fribb has the season but not the offset). Entries
 * without any tracker ID (MAL, AniList, Kitsu) are dropped because no tracker can use them.
 * Kometa's season -1 (absolute numbering) cannot match season/episode references and is ignored.
 */
internal fun parseAnimeIdMappings(
    fribbPayload: String,
    kometaPayload: String?,
): List<AnimeIdMapping> {
    val kometa = kometaPayload
        ?.let { payload -> runCatching { mappingJson.decodeFromString<Map<String, KometaEntry>>(payload) }.getOrNull() }
        .orEmpty()
    return mappingJson.decodeFromString<List<FribbEntry>>(fribbPayload).mapNotNull { entry ->
        if (entry.mal == null && entry.anilist == null && entry.kitsu == null) return@mapNotNull null
        val offsets = entry.anidb
            ?.let { anidb -> kometa[anidb.toString()] }
            ?.takeIf { it.season != null && it.season >= 0 }
        AnimeIdMapping(
            mal = entry.mal,
            anilist = entry.anilist,
            kitsu = entry.kitsu,
            anidb = entry.anidb,
            simkl = entry.simkl,
            imdb = entry.imdb.imdbIds(),
            tmdbTv = entry.tmdb?.tv.firstLong(),
            tmdbMovie = entry.tmdb?.movie.firstLong(),
            tvdb = entry.tvdb ?: offsets?.tvdb,
            tvdbSeason = offsets?.season ?: entry.season?.tvdb?.takeIf { it >= 0 },
            tmdbSeason = entry.season?.tmdb?.takeIf { it >= 0 },
            tvdbEpisodeOffset = offsets?.offset?.coerceAtLeast(0) ?: 0,
            type = entry.type,
        )
    }
}

private fun JsonElement?.firstLong(): Long? = when (this) {
    is JsonArray -> firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.toLongOrNull() }
    is JsonPrimitive -> contentOrNull?.trim()?.toLongOrNull()
    else -> null
}

private fun JsonElement?.imdbIds(): List<String> {
    val values = when (this) {
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> contentOrNull?.split(',').orEmpty()
        else -> emptyList()
    }
    return values.map(String::trim).filter { it.startsWith("tt") }.distinct()
}
