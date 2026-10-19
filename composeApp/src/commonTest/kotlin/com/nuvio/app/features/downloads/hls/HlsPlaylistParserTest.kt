package com.nuvio.app.features.downloads.hls

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HlsPlaylistParserTest {

    private val master = """
        #EXTM3U
        #EXT-X-INDEPENDENT-SEGMENTS
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="Italiano",LANGUAGE="ita",DEFAULT=YES,AUTOSELECT=YES,CHANNELS="2",URI="audio/ita/index.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aac",NAME="English",LANGUAGE="eng",DEFAULT=NO,CHANNELS="6",URI="audio/eng/index.m3u8"
        #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Italiano",LANGUAGE="it",FORCED=YES,URI="/subs/it.m3u8"
        #EXT-X-MEDIA:TYPE=CLOSED-CAPTIONS,GROUP-ID="cc",NAME="CC1",INSTREAM-ID="CC1"
        #EXT-X-STREAM-INF:BANDWIDTH=6200000,AVERAGE-BANDWIDTH=5000000,CODECS="avc1.640028,mp4a.40.2",RESOLUTION=1920x1080,FRAME-RATE=25.000,AUDIO="aac",SUBTITLES="subs"
        video/1080p.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2000000,CODECS="avc1.4d401f,mp4a.40.2",RESOLUTION=1280x720,AUDIO="aac"
        https://cdn.example.com/other/720p.m3u8?token=a,b
        #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=100000,URI="iframes.m3u8"
    """.trimIndent()

    @Test
    fun parsesVariantsAndRenditionsWithResolvedUrls() {
        val playlist = HlsPlaylistParser.parseMultivariant(master, "https://host.tv/movie/master.m3u8?sig=1")

        assertEquals(2, playlist.variants.size)
        val best = playlist.variants[0]
        assertEquals("https://host.tv/movie/video/1080p.m3u8", best.uri)
        assertEquals(1080, best.height)
        assertEquals(5_000_000L, best.averageBandwidth)
        assertEquals(listOf("avc1.640028", "mp4a.40.2"), best.codecs)
        assertEquals("aac", best.audioGroupId)
        assertEquals("subs", best.subtitlesGroupId)
        assertEquals("https://cdn.example.com/other/720p.m3u8?token=a,b", playlist.variants[1].uri)

        val audio = playlist.renditions.filter { it.type == HlsRenditionType.Audio }
        assertEquals(listOf("ita", "eng"), audio.map { it.language })
        assertEquals("https://host.tv/movie/audio/ita/index.m3u8", audio[0].uri)
        assertTrue(audio[0].isDefault)
        assertEquals("6", audio[1].channels)

        val subtitles = playlist.renditions.single { it.type == HlsRenditionType.Subtitles }
        assertEquals("https://host.tv/subs/it.m3u8", subtitles.uri)
        assertTrue(subtitles.forced)

        val captions = playlist.renditions.single { it.type == HlsRenditionType.ClosedCaptions }
        assertNull(captions.uri)
    }

    @Test
    fun parsesMediaPlaylistWithKeysMapsRangesAndDiscontinuities() {
        val media = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:10
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="700@0"
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x0000000000000000000000000000ABCD
            #EXTINF:6.0,
            #EXT-X-BYTERANGE:1000@700
            media.mp4
            #EXTINF:5.5,
            #EXT-X-BYTERANGE:500
            media.mp4
            #EXT-X-DISCONTINUITY
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4,
            ad/seg.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = HlsPlaylistParser.parseMedia(media, "https://host.tv/a/b/index.m3u8")

        assertTrue(playlist.isComplete)
        assertEquals(15.5, playlist.durationSeconds)
        assertEquals(2, playlist.discontinuityCount)
        val (first, second, third) = playlist.segments
        assertEquals(10L, first.mediaSequence)
        assertEquals(HlsByteRange(700, 1000), first.byteRange)
        assertEquals(HlsByteRange(1700, 500), second.byteRange)
        assertEquals("https://host.tv/a/b/init.mp4", first.initSegment?.uri)
        assertEquals(HlsByteRange(0, 700), first.initSegment?.byteRange)
        assertEquals(HlsKeyMethod.Aes128, first.key?.method)
        assertEquals("https://host.tv/a/b/key.bin", first.key?.uri)
        assertContentEquals(ByteArray(14) + byteArrayOf(0xAB.toByte(), 0xCD.toByte()), first.key?.iv)
        assertEquals(1, third.discontinuityIndex)
        assertNull(third.key)
        assertEquals("https://host.tv/a/b/ad/seg.ts", third.uri)
    }

    @Test
    fun detectsLiveAndDrmPlaylists() {
        val live = HlsPlaylistParser.parseMedia("#EXTM3U\n#EXTINF:6,\na.ts\n", "https://h/x.m3u8")
        assertFalse(live.isComplete)
        assertEquals(HlsStreamProblem.Live, HlsStreamAnalyzer.problemOf(live))

        val drm = HlsPlaylistParser.parseMedia(
            "#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://key\",KEYFORMAT=\"com.apple.streamingkeydelivery\"\n#EXTINF:6,\na.ts\n#EXT-X-ENDLIST\n",
            "https://h/x.m3u8",
        )
        assertEquals(HlsStreamProblem.Drm, HlsStreamAnalyzer.problemOf(drm))
    }

    @Test
    fun resolvesRelativeUrls() {
        val base = "https://host.tv/a/b/master.m3u8?x=1"
        assertEquals("https://host.tv/a/b/v.m3u8", resolveHlsUrl(base, "v.m3u8"))
        assertEquals("https://host.tv/a/v.m3u8", resolveHlsUrl(base, "../v.m3u8"))
        assertEquals("https://host.tv/v.m3u8", resolveHlsUrl(base, "/v.m3u8"))
        assertEquals("https://cdn.tv/v.m3u8", resolveHlsUrl(base, "//cdn.tv/v.m3u8"))
        assertEquals("https://host.tv/a/b/master.m3u8?y=2", resolveHlsUrl(base, "?y=2"))
        assertEquals("http://other/v.m3u8", resolveHlsUrl(base, "http://other/v.m3u8"))
        assertEquals("https://host.tv/a/b/d.ts?t=1", resolveHlsUrl("https://host.tv/a/b/", "c/./../d.ts?t=1"))
    }

    @Test
    fun recognisesHlsUrls() {
        assertTrue(HlsStreamAnalyzer.isHlsUrl("https://h/x/index.m3u8"))
        assertTrue(HlsStreamAnalyzer.isHlsUrl("https://h/x/index.M3U8?token=1"))
        assertTrue(HlsStreamAnalyzer.isHlsUrl("https://h/play?format=m3u8&id=2"))
        assertFalse(HlsStreamAnalyzer.isHlsUrl("https://h/x/movie.mp4"))
        assertFalse(HlsStreamAnalyzer.isHlsUrl("https://h/x/manifest.mpd"))
    }
}
