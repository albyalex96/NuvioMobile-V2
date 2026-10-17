package com.nuvio.app.features.cast

import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.parser.StreamParserTag
import com.nuvio.app.features.streams.parser.StreamParserTagKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CastCompatibilityTest {

    @Test
    fun commonFormatsHaveNoIssues() {
        val tags = listOf(
            StreamParserTag(StreamParserTagKind.CODEC, "AVC"),
            StreamParserTag(StreamParserTagKind.AUDIO, "AAC"),
            StreamParserTag(StreamParserTagKind.AUDIO, "DD+"),
            StreamParserTag(StreamParserTagKind.HDR, "HDR10"),
        )
        assertEquals(emptyList(), castCompatibilityIssues(tags))
    }

    @Test
    fun problematicFormatsAreReportedOnceInStableOrder() {
        val tags = listOf(
            StreamParserTag(StreamParserTagKind.AUDIO_PREMIUM, "TrueHD"),
            StreamParserTag(StreamParserTagKind.AUDIO_PREMIUM, "DTS-HD MA"),
            StreamParserTag(StreamParserTagKind.AUDIO, "DTS"),
            StreamParserTag(StreamParserTagKind.DOLBY_VISION, "Dolby Vision"),
            StreamParserTag(StreamParserTagKind.CODEC, "HEVC"),
        )
        assertEquals(listOf("HEVC", "Dolby Vision", "DTS", "TrueHD"), castCompatibilityIssues(tags))
    }

    @Test
    fun issuesAreReadFromTheStreamDescription() {
        val hevc = StreamItem(
            name = "Torrentio\n4K",
            title = "Movie.2023.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-GROUP",
            addonName = "Torrentio",
            addonId = "addon:torrentio",
        )
        val issues = hevc.castCompatibilityIssues("Movie")
        assertTrue("HEVC" in issues)
        assertTrue("Dolby Vision" in issues)

        val avc = StreamItem(
            name = "Torrentio\n1080p",
            title = "Movie.2023.1080p.WEB-DL.AAC2.0.H.264-GROUP",
            addonName = "Torrentio",
            addonId = "addon:torrentio",
        )
        assertFalse(avc.castCompatibilityIssues("Movie").any())
    }
}
