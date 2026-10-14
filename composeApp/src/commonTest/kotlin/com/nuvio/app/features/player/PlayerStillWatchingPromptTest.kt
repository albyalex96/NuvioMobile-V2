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
}
