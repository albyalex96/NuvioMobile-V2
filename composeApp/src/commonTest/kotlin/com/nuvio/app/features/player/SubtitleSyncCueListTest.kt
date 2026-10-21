package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleSyncCueListTest {
    private val cues = listOf(1_000L, 5_000L, 9_000L, 20_000L).map { SubtitleSyncCue(startTimeMs = it, text = "line $it") }

    @Test
    fun findsTheLineClosestToTheCapturedMoment() {
        assertEquals(0, nearestSubtitleCueIndex(cues, 0L))
        assertEquals(2, nearestSubtitleCueIndex(cues, 10_000L))
        assertEquals(3, nearestSubtitleCueIndex(cues, 60_000L))
        assertEquals(-1, nearestSubtitleCueIndex(emptyList(), 10_000L))
    }

    @Test
    fun formatsTheOffsetFromTheCapturedMoment() {
        assertEquals("+0.0s", formatCueOffset(0L))
        assertEquals("+1.2s", formatCueOffset(1_234L))
        assertEquals("-0.5s", formatCueOffset(-460L))
        assertEquals("-12.0s", formatCueOffset(-12_000L))
    }
}
