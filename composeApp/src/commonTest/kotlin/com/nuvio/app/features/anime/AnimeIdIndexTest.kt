package com.nuvio.app.features.anime

import com.nuvio.app.features.tracking.TrackingEpisode
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingProviderId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimeIdIndexTest {
    private val fribb = """
        [
          {"type":"TV","anidb_id":10,"mal_id":100,"kitsu_id":1000,"anilist_id":100,"imdb_id":["tt0001"],
           "themoviedb_id":{"tv":500},"tvdb_id":9000,"season":{"tvdb":1,"tmdb":1}},
          {"type":"TV","anidb_id":11,"mal_id":101,"imdb_id":["tt0001"],"themoviedb_id":{"tv":500},
           "tvdb_id":9000,"season":{"tvdb":1,"tmdb":1}},
          {"type":"TV","anidb_id":12,"mal_id":102,"imdb_id":["tt0001"],"tvdb_id":9000,"season":{"tvdb":2,"tmdb":2}},
          {"type":"MOVIE","anidb_id":13,"mal_id":103,"imdb_id":["tt0002"],"themoviedb_id":{"movie":[600]}},
          {"type":"TV","anidb_id":14,"imdb_id":["tt0003"]}
        ]
    """.trimIndent()

    private val kometa = """
        {
          "10": {"tvdb_id": 9000, "tvdb_season": 1, "tvdb_epoffset": 0, "mal_id": 100},
          "11": {"tvdb_id": 9000, "tvdb_season": 1, "tvdb_epoffset": 12, "mal_id": 101},
          "12": {"tvdb_id": 9000, "tvdb_season": -1, "tvdb_epoffset": 0, "mal_id": 102}
        }
    """.trimIndent()

    private val index = AnimeIdIndex(parseAnimeIdMappings(fribb, kometa))

    @Test
    fun parserKeepsOnlyEntriesWithTrackerIdsAndJoinsOffsets() {
        assertEquals(4, index.size)
        assertEquals(12, index.byMal(101)?.tvdbEpisodeOffset)
        // Kometa absolute numbering (-1) is ignored, Fribb's season stays.
        assertEquals(2, index.byMal(102)?.tvdbSeason)
        assertEquals(listOf("tt0001"), index.byMal(100)?.imdb)
        assertEquals(600L, index.byMal(103)?.tmdbMovie)
    }

    @Test
    fun episodesPastTheOffsetBelongToTheSecondCour() {
        assertEquals(100L to 5, resolve(imdb = "tt0001", season = 1, episode = 5))
        assertEquals(100L to 12, resolve(imdb = "tt0001", season = 1, episode = 12))
        assertEquals(101L to 1, resolve(imdb = "tt0001", season = 1, episode = 13))
        assertEquals(102L to 3, resolve(tvdb = "9000", season = 2, episode = 3))
        assertNull(resolve(imdb = "tt0001", season = 3, episode = 1))
    }

    @Test
    fun trackerIdsAreUsedDirectlyWithTheirOwnNumbering() {
        assertEquals(100L to 7, resolve(kitsu = 1000L, season = null, episode = 7))
        assertEquals(555L to 2, resolve(mal = 555L, season = 1, episode = 2))
    }

    @Test
    fun moviesResolveToTheMovieEntry() {
        val movie = TrackingMediaReference(
            kind = TrackingMediaKind.MOVIE,
            ids = TrackingExternalIds(imdb = "tt0002"),
        )
        val target = index.resolve(movie)
        assertEquals(103L, target?.mapping?.mal)
        assertEquals(1, target?.episode)
    }

    @Test
    fun knownAnimeAndPrimaryEntry() {
        assertTrue(index.isKnownAnime(TrackingExternalIds(imdb = "tt0001")))
        assertTrue(index.isKnownAnime(TrackingExternalIds(tmdb = 500L)))
        assertFalse(index.isKnownAnime(TrackingExternalIds(imdb = "tt9999")))
        assertEquals(100L, index.primaryEntry(TrackingExternalIds(imdb = "tt0001"))?.mal)
    }

    @Test
    fun selectorDropsExcludedProvidersOnlyForAnime() = runTest {
        val providers = listOf(TrackingProviderId.SIMKL, TrackingProviderId.MAL)
        val excluded = setOf(TrackingProviderId.SIMKL)
        val forAnime = filterProvidersForMedia(
            providers = providers,
            providerId = { it },
            isAnime = { true },
            isEnabledForAnime = { it !in excluded },
        )
        assertEquals(listOf(TrackingProviderId.MAL), forAnime)
        val forShow = filterProvidersForMedia(
            providers = providers,
            providerId = { it },
            isAnime = { false },
            isEnabledForAnime = { it !in excluded },
        )
        assertEquals(providers, forShow)
    }

    @Test
    fun excludedProvidersRoundTrip() {
        assertEquals(
            setOf(TrackingProviderId.SIMKL, TrackingProviderId.MAL),
            decodeExcludedProviders("simkl, mal,unknown,"),
        )
        assertEquals(emptySet(), decodeExcludedProviders(null))
    }

    private fun resolve(
        imdb: String? = null,
        tvdb: String? = null,
        kitsu: Long? = null,
        mal: Long? = null,
        season: Int?,
        episode: Int,
    ): Pair<Long?, Int>? {
        val media = TrackingMediaReference(
            kind = TrackingMediaKind.SHOW,
            ids = TrackingExternalIds(imdb = imdb, tvdb = tvdb, kitsu = kitsu, mal = mal),
            episode = TrackingEpisode(season = season, number = episode),
        )
        return index.resolve(media)?.let { it.mapping.mal to it.episode }
    }
}
