package com.nuvio.app.core.mediaproxy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaManifestRewriterTest {
    private val proxify: (String) -> String = { "P[$it]" }

    @Test
    fun hlsSegmentsAndAttributesAreRoutedThroughTheProxy() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x1
            #EXT-X-MAP:URI="/init.mp4"
            #EXTINF:6.0,
            seg-1.ts?token=a
            #EXTINF:6.0,
            https://cdn.example.com/other/seg-2.ts
        """.trimIndent()

        val rewritten = rewriteHlsPlaylist(playlist, "https://origin.example.com/hls/v1/index.m3u8?sig=1", proxify)

        assertEquals(
            """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-KEY:METHOD=AES-128,URI="P[https://origin.example.com/hls/v1/key.bin]",IV=0x1
            #EXT-X-MAP:URI="P[https://origin.example.com/init.mp4]"
            #EXTINF:6.0,
            P[https://origin.example.com/hls/v1/seg-1.ts?token=a]
            #EXTINF:6.0,
            P[https://cdn.example.com/other/seg-2.ts]
            """.trimIndent(),
            rewritten,
        )
    }

    @Test
    fun hlsMasterPlaylistRewritesVariantsAndRenditions() {
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",URI="audio/en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,AUDIO="aud"
            video/720.m3u8
        """.trimIndent()

        val rewritten = rewriteHlsPlaylist(playlist, "http://host:8080/live/master.m3u8", proxify)

        assertTrue("URI=\"P[http://host:8080/live/audio/en.m3u8]\"" in rewritten)
        assertTrue(rewritten.endsWith("P[http://host:8080/live/video/720.m3u8]"))
    }

    @Test
    fun nonHttpKeyUrisAreLeftUntouched() {
        val playlist = "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key-id\"\n#EXT-X-SESSION-KEY:METHOD=AES-128,URI=\"data:text/plain;base64,AA==\""
        assertEquals(playlist, rewriteHlsPlaylist(playlist, "https://a.example/x.m3u8", proxify))
    }

    @Test
    fun dashAbsoluteUrlsAreRewrittenAndRelativeOnesKept() {
        val manifest = """<MPD><BaseURL>https://cdn.example.com/v/</BaseURL><SegmentTemplate media="seg-${'$'}Number${'$'}.m4s" initialization="https://cdn.example.com/init.mp4"/></MPD>"""

        val rewritten = rewriteDashManifest(manifest, "https://a.example/m.mpd", "https://a.example/m.mpd", proxify)

        assertEquals(
            """<MPD><BaseURL>P[https://cdn.example.com/v/]</BaseURL><SegmentTemplate media="seg-${'$'}Number${'$'}.m4s" initialization="P[https://cdn.example.com/init.mp4]"/></MPD>""",
            rewritten,
        )
    }

    @Test
    fun redirectedDashManifestGetsABaseUrlForItsFinalLocation() {
        val manifest = """<MPD type="static"><Period/></MPD>"""

        val rewritten = rewriteDashManifest(
            manifest,
            requestedUrl = "https://short.example/m.mpd",
            baseUrl = "https://cdn.example.com/path/m.mpd",
            proxify = proxify,
        )

        assertEquals("""<MPD type="static"><BaseURL>P[https://cdn.example.com/path/]</BaseURL><Period/></MPD>""", rewritten)
    }

    @Test
    fun byteRangesAreParsedAndClamped() {
        assertEquals(0L..99L, parseRange("bytes=0-99", 1_000))
        assertEquals(500L..999L, parseRange("bytes=500-", 1_000))
        assertEquals(900L..999L, parseRange("bytes=-100", 1_000))
        assertEquals(990L..999L, parseRange("bytes=990-5000", 1_000))
        assertNull(parseRange("bytes=1000-", 1_000))
        assertNull(parseRange("bytes=abc", 1_000))
    }
}
