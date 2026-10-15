package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerStillWatchingPromptTest {
    private val enabled = PlayerSettingsUiState(
        stillWatchingPromptEnabled = true,
        stillWatchingEpisodeCount = 3,
    )

    @Test
    fun nightWindowCoversTenPmToFourAm() {
        assertTrue(isStillWatchingNightHour(22))
        assertTrue(isStillWatchingNightHour(23))
        assertTrue(isStillWatchingNightHour(0))
        assertTrue(isStillWatchingNightHour(3))
        assertFalse(isStillWatchingNightHour(4))
        assertFalse(isStillWatchingNightHour(12))
        assertFalse(isStillWatchingNightHour(21))
    }

    @Test
    fun promptsOnlyWhenEnabledAndThresholdReached() {
        assertFalse(shouldPromptStillWatching(enabled.copy(stillWatchingPromptEnabled = false), 5) { 12 })
        assertFalse(shouldPromptStillWatching(enabled, 2) { 12 })
        assertTrue(shouldPromptStillWatching(enabled, 3) { 12 })
    }

    @Test
    fun nightOnlyRespectsLocalHour() {
        val nightOnly = enabled.copy(stillWatchingNightOnly = true)
        assertFalse(shouldPromptStillWatching(nightOnly, 3) { 15 })
        assertTrue(shouldPromptStillWatching(nightOnly, 3) { 23 })
        assertFalse(shouldPromptStillWatching(nightOnly, 3) { null })
    }

    @Test
    fun liveTvPromptsAfterConfiguredMinutesOnlyWhenEnabled() {
        val liveTv = PlayerSettingsUiState(stillWatchingLiveTvEnabled = true, stillWatchingLiveTvMinutes = 30)
        assertFalse(shouldPromptStillWatchingLiveTv(liveTv.copy(stillWatchingLiveTvEnabled = false), 3_600_000L))
        assertFalse(shouldPromptStillWatchingLiveTv(liveTv, 29L * 60_000L))
        assertTrue(shouldPromptStillWatchingLiveTv(liveTv, 30L * 60_000L))
    }

    @Test
    fun liveTvMinutesAreClampedAndSnappedToTheSelectorStep() {
        kotlin.test.assertEquals(15, normalizeStillWatchingLiveTvMinutes(1))
        kotlin.test.assertEquals(360, normalizeStillWatchingLiveTvMinutes(1_000))
        kotlin.test.assertEquals(90, normalizeStillWatchingLiveTvMinutes(95))
        kotlin.test.assertEquals(105, normalizeStillWatchingLiveTvMinutes(100))
    }
}
