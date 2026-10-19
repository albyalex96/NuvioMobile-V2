package com.nuvio.app.features.cast

import com.nuvio.app.features.downloads.hls.HlsPlaylistParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CastHlsFormatTest {

    private fun formatOf(playlist: String) =
        castHlsSegmentFormatOf(HlsPlaylistParser.parseMedia(playlist.trimIndent(), "https://cdn.example.com/hls/index.m3u8"))

    @Test
    fun tsSegmentsAreTs() {
        assertEquals(
            CastHlsSegmentFormat.Ts,
            formatOf(
                """
                #EXTM3U
                #EXT-X-TARGETDURATION:6
                #EXTINF:6.0,
                seg0.ts?token=1
                #EXT-X-ENDLIST
                """,
            ),
        )
    }

    @Test
    fun initSegmentMeansFmp4() {
        assertEquals(
            CastHlsSegmentFormat.Fmp4,
            formatOf(
                """
                #EXTM3U
                #EXT-X-TARGETDURATION:6
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:6.0,
                seg0
                #EXT-X-ENDLIST
                """,
            ),
        )
    }

    @Test
    fun packedAudioAndDisguisedSegments() {
        assertEquals(
            CastHlsSegmentFormat.Aac,
            formatOf(
                """
                #EXTM3U
                #EXTINF:6.0,
                audio0.aac
                """,
            ),
        )
        // TS segments disguised as images keep the TS default.
        assertEquals(
            CastHlsSegmentFormat.Ts,
            formatOf(
                """
                #EXTM3U
                #EXTINF:6.0,
                https://img.example.com/0001.png
                """,
            ),
        )
    }

    @Test
    fun m4sWithoutInitSectionIsTs() {
        assertEquals(
            CastHlsSegmentFormat.Ts,
            formatOf(
                """
                #EXTM3U
                #EXTINF:4,
                https://cdn.example.boats/video/0001-0500.m4s?token=1
                #EXTINF:4,
                https://cdn.example.boats/video/0002-0250.html?token=1
                """,
            ),
        )
    }

    @Test
    fun emptyPlaylistHasNoFormat() {
        assertNull(formatOf("#EXTM3U\n#EXT-X-ENDLIST"))
    }
}
