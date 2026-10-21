package com.nuvio.app.features.subdl

import com.nuvio.app.features.player.AddonSubtitleSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubDlSubtitlesTest {
    @Test
    fun parsesMovieAndEpisodeIds() {
        assertEquals(SubDlQuery(imdbId = "tt0133093"), subDlQueryFor("movie", "tt0133093"))
        assertEquals(
            SubDlQuery(imdbId = "tt0944947", season = 1, episode = 2),
            subDlQueryFor("series", "tt0944947:1:2"),
        )
        assertEquals(SubDlQuery(tmdbId = 1399, season = 3, episode = 4), subDlQueryFor("tv", "tmdb:1399:3:4"))
        assertNull(subDlQueryFor("series", "tt0944947"))
        assertNull(subDlQueryFor("movie", "kitsu:123"))
    }

    @Test
    fun mapsLanguagesBothWays() {
        assertEquals("IT", toSubDlLanguage("it"))
        assertEquals("IT", toSubDlLanguage("it-IT"))
        assertEquals("BR_PT", toSubDlLanguage("pt-br"))
        assertEquals("PT", toSubDlLanguage("pt"))
        assertEquals("ZH_BG", toSubDlLanguage("zh-tw"))
        assertEquals("ZH", toSubDlLanguage("zh"))
        assertNull(toSubDlLanguage("none"))
        assertNull(toSubDlLanguage("xx"))

        assertEquals("pt-br", fromSubDlLanguage("BR_PT"))
        assertEquals("zh-tw", fromSubDlLanguage("ZH_BG"))
        assertEquals("unknown", fromSubDlLanguage(null))
    }

    @Test
    fun placeholderRoundTrip() {
        val movie = SubDlPlaceholder("/subtitle/3197651-3213944.zip")
        assertEquals("subdl://file/subtitle/3197651-3213944.zip", movie.url)
        assertEquals(movie, parseSubDlPlaceholder(movie.url))

        val episode = SubDlPlaceholder("/subtitle/1-2.zip", season = 1, episode = 2)
        assertEquals(episode, parseSubDlPlaceholder(episode.url))
        assertEquals("1-2_s1e2", episode.cacheName)
        assertNull(parseSubDlPlaceholder("https://example.com/a.srt"))
    }

    @Test
    fun filtersBySettingsAndEpisode() {
        val query = SubDlQuery(imdbId = "tt1", season = 1, episode = 2)
        val items = listOf(
            SubDlSubtitleItem(url = "/a.zip", season = 1, episode = 2),
            SubDlSubtitleItem(url = "/b.zip", season = 1, episode = 3),
            SubDlSubtitleItem(url = "/c.zip", season = 1, fullSeason = true),
            SubDlSubtitleItem(url = "/d.zip", season = 1, episodeFrom = 3, episodeEnd = 6),
            SubDlSubtitleItem(url = "/e.zip", season = 1, episode = 2, hi = true),
            SubDlSubtitleItem(url = "/f.zip", season = 1, episode = 2, aiTranslated = true),
            SubDlSubtitleItem(url = null, season = 1, episode = 2),
        )
        val defaults = items.filterFor(query, SubDlSettings())
        assertEquals(listOf("/a.zip", "/c.zip", "/e.zip"), defaults.map { it.url })

        val strict = items.filterFor(query, SubDlSettings(includeHearingImpaired = false, includeSeasonPacks = false))
        assertEquals(listOf("/a.zip"), strict.map { it.url })
    }

    @Test
    fun buildsPanelEntries() {
        val query = SubDlQuery(imdbId = "tt1", season = 1, episode = 2)
        val subtitle = SubDlSubtitleItem(
            releaseName = "Show.S01.WEB",
            language = "IT",
            url = "/subtitle/9-9.zip",
            fullSeason = true,
            hi = true,
        ).toAddonSubtitle(query)!!
        assertEquals("subdl://file/subtitle/9-9.zip?episode=1:2", subtitle.url)
        assertEquals("it", subtitle.language)
        assertEquals("Show.S01.WEB · SDH · Pack", subtitle.display)
        assertEquals(AddonSubtitleSource.SubDl, subtitle.source)
    }

    @Test
    fun picksEpisodeFromSeasonPack() {
        val entries = listOf("Show.S01E01.srt", "Show.S01E02.srt", "Show.S01E10.srt", "__MACOSX/._Show.S01E02.srt", "readme.txt")
            .map { ZipEntryData(it, it.encodeToByteArray()) }
        assertEquals("Show.S01E02.srt", pickSubDlEntry(entries, season = 1, episode = 2)?.name)
        assertEquals("Show.S01E10.srt", pickSubDlEntry(entries, season = 1, episode = 10)?.name)
        val crossFormat = listOf("show.1x01.srt", "show.1x02.srt").map { ZipEntryData(it, ByteArray(0)) }
        assertEquals("show.1x02.srt", pickSubDlEntry(crossFormat, season = 1, episode = 2)?.name)
        assertEquals("Show.S01E01.srt", pickSubDlEntry(entries, season = null, episode = null)?.name)
    }

    @Test
    fun decodesSubtitleEncodings() {
        assertEquals("perché", decodeSubtitleBytes("perché".encodeToByteArray()))
        assertEquals("perché", decodeSubtitleBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "perché".encodeToByteArray()))
        // Windows-1252: "perché €"
        val cp1252 = byteArrayOf(0x70, 0x65, 0x72, 0x63, 0x68, 0xE9.toByte(), 0x20, 0x80.toByte())
        assertEquals("perché €", decodeSubtitleBytes(cp1252))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x63, 0x00, 0x69, 0x00, 0x61, 0x00, 0x6F, 0x00)
        assertEquals("ciao", decodeSubtitleBytes(utf16))
        assertTrue(decodeSubtitleBytes(ByteArray(0)).isEmpty())
    }

    @Test
    fun seasonSearchKeepsOnlyTheEpisode() {
        val query = SubDlQuery(imdbId = "tt1586680", season = 5, episode = 8)
        assertTrue(SubDlSubtitleItem(url = "/a.zip", fullSeason = true).belongsToEpisode(query))
        assertTrue(SubDlSubtitleItem(url = "/b.zip", episode = 8).belongsToEpisode(query))
        assertTrue(!SubDlSubtitleItem(url = "/c.zip", episode = 3).belongsToEpisode(query))
        assertTrue(SubDlSubtitleItem(url = "/d.zip", releaseName = "Shameless.US.S05E08.HDTV").belongsToEpisode(query))
        assertTrue(SubDlSubtitleItem(url = "/e.zip", releaseName = "Shameless 5x08 ita").belongsToEpisode(query))
        assertTrue(!SubDlSubtitleItem(url = "/f.zip", releaseName = "Shameless.US.S05E18.HDTV").belongsToEpisode(query))
        assertTrue(!SubDlSubtitleItem(url = "/g.zip", releaseName = "Shameless.US.S05.ita").belongsToEpisode(query))
    }

    @Test
    fun searchUrlCoversSeasonOnlyQueries() {
        val episode = SubDlApi.searchUrl("k", SubDlQuery(imdbId = "tt1", season = 5, episode = 8), listOf("IT"))
        assertTrue("type=tv" in episode && "season_number=5" in episode && "episode_number=8" in episode)
        val season = SubDlApi.searchUrl("k", SubDlQuery(imdbId = "tt1", season = 5), listOf("IT"))
        assertTrue("type=tv" in season && "season_number=5" in season && "episode_number" !in season)
        val movie = SubDlApi.searchUrl("k", SubDlQuery(imdbId = "tt1"), emptyList())
        assertTrue("type=movie" in movie && "languages" !in movie)
    }
}
