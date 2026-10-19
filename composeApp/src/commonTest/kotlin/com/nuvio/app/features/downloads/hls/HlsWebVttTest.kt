package com.nuvio.app.features.downloads.hls

import kotlin.test.Test
import kotlin.test.assertEquals

class HlsWebVttTest {

    @Test
    fun parsesCuesAndTheTimestampMap() {
        val segment = HlsWebVtt.parseSegment(
            """
            WEBVTT
            X-TIMESTAMP-MAP=MPEGTS:900000,LOCAL:00:00:00.000

            NOTE a comment

            1
            00:00:01.500 --> 00:00:03.000 align:start
            <v Mario>Ciao <i>mondo</i></v>

            00:01:02.000 --> 00:01:04.250
            Riga &amp; due<c.yellow>!</c>
            """.trimIndent(),
        )

        assertEquals(900_000L, segment.mpegTs)
        assertEquals(0L, segment.localUs)
        assertEquals(
            listOf(
                HlsSubtitleCue(1_500_000L, 3_000_000L, "Ciao <i>mondo</i>"),
                HlsSubtitleCue(62_000_000L, 64_250_000L, "Riga & due!"),
            ),
            segment.cues,
        )
    }

    @Test
    fun mapsCuesOnTheVideoTimeline() {
        val segment = HlsWebVttSegment(
            cues = listOf(HlsSubtitleCue(1_000_000L, 2_000_000L, "a")),
            mpegTs = 900_000L, // 10 s
            localUs = 0L,
        )
        // The video of this group starts at 10 s (MPEG-2 time) and is placed at 0 on the output.
        val mapped = HlsWebVtt.mapCues(segment, referencePts = 900_000L) { mpegUs -> mpegUs - 10_000_000L }

        assertEquals(listOf(HlsSubtitleCue(1_000_000L, 2_000_000L, "a")), mapped)
    }

    @Test
    fun keepsPlaylistTimesWithoutATimestampMap() {
        val segment = HlsWebVttSegment(listOf(HlsSubtitleCue(5_000_000L, 6_000_000L, "a")), mpegTs = null, localUs = 0L)

        assertEquals(segment.cues, HlsWebVtt.mapCues(segment, referencePts = 123L) { error("not used") })
    }

    @Test
    fun unwrapsTimestampsAroundThe33BitLimit() {
        val wrap = 1L shl 33
        assertEquals(wrap + 100L, HlsWebVtt.unwrapPts(100L, wrap - 1_000L))
        assertEquals(wrap - 100L, HlsWebVtt.unwrapPts(wrap - 100L, wrap + 1_000L))
        assertEquals(500L, HlsWebVtt.unwrapPts(500L, null))
    }

    @Test
    fun mergesRepeatedAndSplitCues() {
        val merged = HlsWebVtt.mergeCues(
            listOf(
                HlsSubtitleCue(4_000_000L, 6_000_000L, "b"),
                HlsSubtitleCue(1_000_000L, 2_000_000L, "a"),
                HlsSubtitleCue(1_000_000L, 2_000_000L, "a"),
                HlsSubtitleCue(6_000_000L, 7_000_000L, "b"),
            ),
        )

        assertEquals(
            listOf(HlsSubtitleCue(1_000_000L, 2_000_000L, "a"), HlsSubtitleCue(4_000_000L, 7_000_000L, "b")),
            merged,
        )
    }

    @Test
    fun writesSrt() {
        val srt = HlsWebVtt.toSrt(listOf(HlsSubtitleCue(3_723_004_000L, 3_724_000_000L, "Ciao")))

        assertEquals("1\n01:02:03,004 --> 01:02:04,000\nCiao\n\n", srt)
    }
}
