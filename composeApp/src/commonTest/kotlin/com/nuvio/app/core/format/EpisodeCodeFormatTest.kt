package com.nuvio.app.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeCodeFormatTest {
    @Test
    fun formatsSeasonAndEpisode() {
        assertEquals("S01E02", EpisodeCodeFormat.S_PADDED.format(1, 2))
        assertEquals("S1E2", EpisodeCodeFormat.S_PLAIN.format(1, 2))
        assertEquals("01x02", EpisodeCodeFormat.X_PADDED.format(1, 2))
        assertEquals("1x2", EpisodeCodeFormat.X_PLAIN.format(1, 2))
    }

    @Test
    fun doesNotTruncateLargeNumbers() {
        assertEquals("S12E105", EpisodeCodeFormat.S_PADDED.format(12, 105))
        assertEquals("12x105", EpisodeCodeFormat.X_PADDED.format(12, 105))
    }

    @Test
    fun formatsEpisodeOnly() {
        assertEquals("E02", EpisodeCodeFormat.S_PADDED.format(null, 2))
        assertEquals("E2", EpisodeCodeFormat.X_PLAIN.format(null, 2))
    }

    @Test
    fun defaultAndMissingEpisodeReturnNull() {
        assertNull(EpisodeCodeFormat.DEFAULT.format(1, 2))
        assertNull(EpisodeCodeFormat.S_PADDED.format(1, null))
    }

    @Test
    fun parsesKeysWithDefaultFallback() {
        assertEquals(EpisodeCodeFormat.X_PLAIN, EpisodeCodeFormat.fromKey("1x2"))
        assertEquals(EpisodeCodeFormat.DEFAULT, EpisodeCodeFormat.fromKey(null))
        assertEquals(EpisodeCodeFormat.DEFAULT, EpisodeCodeFormat.fromKey("bogus"))
    }
}
