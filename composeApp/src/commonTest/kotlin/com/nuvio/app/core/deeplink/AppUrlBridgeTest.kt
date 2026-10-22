package com.nuvio.app.core.deeplink

import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppUrlBridgeTest {

    @Test
    fun `parses party invites from the app and the website`() {
        assertEquals(AppDeepLink.Party("K7M2QX"), parseAppDeepLink("nuvio://party/k7m-2qx"))
        assertEquals(AppDeepLink.Party("K7M2QX"), parseAppDeepLink("https://nuvio.tv/party/K7M2QX"))
        assertEquals(AppDeepLink.Party("K7M2QX"), parseAppDeepLink("https://NUVIO.tv/party/K7M2QX/"))
        assertNull(parseAppDeepLink("https://nuvio.tv/party/K7M2Q"))
        assertNull(parseAppDeepLink("https://nuvio.tv/link?code=K7M2QX"))
        assertNull(parseAppDeepLink("https://evil.example/party/K7M2QX"))
        assertNull(parseAppDeepLink("http://nuvio.tv/party/K7M2QX"))
    }

    @Test
    fun `parses external search links and drops the app name`() {
        assertEquals(AppDeepLink.Search("Criminal Minds"), parseAppDeepLink(buildSearchDeepLinkUrl("Criminal Minds")))
        assertEquals(AppDeepLink.Search("criminal minds"), parseAppDeepLink("nuvio://search?q=criminal%20minds%20su%20Nuvio"))
        assertEquals(AppDeepLink.Search("Dune"), parseAppDeepLink("nuvio://search?query=Dune%20on%20Nuvio%20Enhanced%20TV"))
        assertEquals(AppDeepLink.Search("Lost"), parseAppDeepLink("nuvio://search/Lost"))
        assertNull(parseAppDeepLink("nuvio://search?q=%20"))
    }

    @Test
    fun `parses existing notification meta deeplink`() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            parseAppDeepLink("nuvio://meta?type=series&id=tt0944947"),
        )
    }

    @Test
    fun `parses direct nuvio addon install deeplink`() {
        assertEquals(
            AppDeepLink.AddonInstall("https://free.nebulapro.xyz/sports/i/free/manifest.json"),
            parseAppDeepLink("nuvio://free.nebulapro.xyz/sports/i/free/manifest.json"),
        )
    }

    @Test
    fun `parses stremio addon install deeplink`() {
        assertEquals(
            AppDeepLink.AddonInstall("https://free.nebulapro.xyz/sports/i/free/manifest.json"),
            parseAppDeepLink("stremio://free.nebulapro.xyz/sports/i/free/manifest.json"),
        )
    }

    @Test
    fun `parses direct imdb detail deeplink`() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            parseAppDeepLink("nuvio://series/tt0944947"),
        )
    }

    @Test
    fun `parses provider imdb detail deeplink`() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tt0944947"),
            parseAppDeepLink("nuvio://imdb/series/tt0944947"),
        )
    }

    @Test
    fun `parses provider tmdb detail deeplink`() {
        assertEquals(
            AppDeepLink.Meta(type = "series", id = "tmdb:1399"),
            parseAppDeepLink("nuvio://tmdb/tv/1399"),
        )
    }

    @Test
    fun `does not treat reserved auth link as addon install`() {
        assertNull(parseAppDeepLink("nuvio://auth/trakt?code=abc"))
    }

    @Test
    fun `does not treat non-host stremio link as addon install`() {
        assertNull(parseAppDeepLink("stremio://detail/series/tt0944947"))
    }

    @Test
    fun `continue watching links round trip with special characters`() {
        val item = ContinueWatchingItem(
            parentMetaId = "tt0944947",
            parentMetaType = "series",
            videoId = "tt0944947:2:5",
            title = "Il Trono di Spade & co / 100% \"test\"",
            subtitle = "",
            imageUrl = "https://img.example/thumb.jpg?w=300&h=170",
            logo = "https://img.example/logo.png",
            poster = "https://img.example/poster.jpg",
            background = "https://img.example/bg.jpg",
            seasonNumber = 2,
            episodeNumber = 5,
            episodeTitle = "L'episodio #5",
            episodeThumbnail = "https://img.example/thumb.jpg?w=300&h=170",
            resumePositionMs = 754_000L,
            durationMs = 3_300_000L,
            progressFraction = 0.25f,
        )
        val parsed = parseAppDeepLink(buildContinueWatchingDeepLinkUrl(item, ContinueWatchingLaunchAction.ChooseSource))
        assertEquals(AppDeepLink.ContinueWatching(item, ContinueWatchingLaunchAction.ChooseSource), parsed)
    }

    @Test
    fun `continue watching links need type and id and default to play`() {
        assertNull(parseAppDeepLink("nuvio://continue?type=movie"))
        val parsed = parseAppDeepLink("nuvio://continue?type=movie&id=tt0133093&action=unknown")
        assertEquals(
            AppDeepLink.ContinueWatching(
                item = ContinueWatchingItem(
                    parentMetaId = "tt0133093",
                    parentMetaType = "movie",
                    videoId = "tt0133093",
                    title = "",
                    subtitle = "",
                    imageUrl = null,
                    resumePositionMs = 0L,
                    durationMs = 0L,
                    progressFraction = 0f,
                ),
                action = ContinueWatchingLaunchAction.Play,
            ),
            parsed,
        )
    }
}
