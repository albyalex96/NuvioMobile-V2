package com.nuvio.app.features.opensubtitles

import com.nuvio.app.features.player.AddonSubtitleSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenSubtitlesSubtitlesTest {
    @Test
    fun parsesMovieAndEpisodeIds() {
        assertEquals(OpenSubtitlesQuery(imdbId = 133093), openSubtitlesQueryFor("movie", "tt0133093"))
        assertEquals(
            OpenSubtitlesQuery(imdbId = 944947, season = 1, episode = 2),
            openSubtitlesQueryFor("series", "tt0944947:1:2"),
        )
        assertEquals(
            OpenSubtitlesQuery(tmdbId = 1399, season = 3, episode = 4),
            openSubtitlesQueryFor("tv", "tmdb:1399:3:4"),
        )
        assertNull(openSubtitlesQueryFor("series", "tt0944947"))
        assertNull(openSubtitlesQueryFor("movie", "kitsu:123"))
    }

    @Test
    fun mapsLanguagesBothWays() {
        assertEquals("pt-pt", toOpenSubtitlesLanguage("pt"))
        assertEquals("pt-br", toOpenSubtitlesLanguage("pt-BR"))
        assertEquals("ea", toOpenSubtitlesLanguage("es-419"))
        assertEquals("it", toOpenSubtitlesLanguage("it"))
        assertNull(toOpenSubtitlesLanguage("none"))
        assertEquals("it", toOpenSubtitlesLanguage("it-IT"))
        assertEquals("en", toOpenSubtitlesLanguage("en_US"))
        assertEquals("zh-tw", toOpenSubtitlesLanguage("zh-TW"))
        assertEquals("es-419", fromOpenSubtitlesLanguage("ea"))
        assertEquals("pt", fromOpenSubtitlesLanguage("pt-PT"))
        assertEquals("en", fromOpenSubtitlesLanguage("en"))
    }

    @Test
    fun searchUrlHasSortedLowercaseParameters() {
        val url = OpenSubtitlesApi.searchUrl(
            query = OpenSubtitlesQuery(imdbId = 944947, season = 1, episode = 2),
            languages = listOf("it", "en"),
            settings = OpenSubtitlesSettings(includeHearingImpaired = false),
        )
        assertEquals(
            "https://api.opensubtitles.com/api/v1/subtitles?ai_translated=exclude&episode_number=2" +
                "&hearing_impaired=exclude&languages=en,it&machine_translated=exclude" +
                "&parent_imdb_id=944947&season_number=1",
            url,
        )
    }

    @Test
    fun detectsTheRealSubtitleFormat() {
        assertEquals("srt", subtitleExtensionForText("1\n00:00:01,000 --> 00:00:02,000\nCiao\n"))
        assertEquals("vtt", subtitleExtensionForText("\uFEFFWEBVTT\n\n00:01.000 --> 00:02.000\nCiao"))
        assertEquals("ass", subtitleExtensionForText("[Script Info]\nTitle: x\n"))
    }

    @Test
    fun searchItemsBecomePlaceholderSubtitles() {
        val item = OpenSubtitlesSearchItem(
            id = "1",
            attributes = OpenSubtitlesAttributes(
                language = "it",
                release = "Show.S01E02.1080p",
                hearingImpaired = true,
                files = listOf(OpenSubtitlesFile(fileId = 42)),
            ),
        )
        val subtitle = item.toAddonSubtitles().single()
        assertEquals("opensubtitles://file/42", subtitle.url)
        assertEquals("it", subtitle.language)
        assertEquals(AddonSubtitleSource.OpenSubtitles, subtitle.source)
        assertEquals(OPENSUBTITLES_PROVIDER_NAME, subtitle.addonName)
        assertTrue(subtitle.display.startsWith("Show.S01E02.1080p"))
        assertTrue(isOpenSubtitlesPlaceholderUrl(subtitle.url))
    }
}
