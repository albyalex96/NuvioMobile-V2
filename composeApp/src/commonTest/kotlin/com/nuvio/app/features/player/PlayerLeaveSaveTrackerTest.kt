package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerLeaveSaveTrackerTest {

    @Test
    fun savesTheFirstLeaveSignal() {
        assertTrue(PlayerLeaveSaveTracker().shouldSave(120_000L))
    }

    @Test
    fun skipsSignalsAtTheSamePosition() {
        val tracker = PlayerLeaveSaveTracker()
        tracker.shouldSave(120_000L)

        assertFalse(tracker.shouldSave(120_400L))
    }

    @Test
    fun savesAgainWhenThePositionMoved() {
        val tracker = PlayerLeaveSaveTracker()
        tracker.shouldSave(120_000L)

        assertTrue(tracker.shouldSave(120_000L + PlayerLeaveSaveToleranceMs))
    }

    @Test
    fun markedSaveSkipsTheFollowingSignals() {
        val tracker = PlayerLeaveSaveTracker()
        tracker.markSaved(120_000L)

        assertFalse(tracker.shouldSave(120_200L))
    }

    @Test
    fun resetAllowsSavingTheSamePositionAgain() {
        val tracker = PlayerLeaveSaveTracker()
        tracker.shouldSave(120_000L)
        tracker.reset()

        assertTrue(tracker.shouldSave(120_000L))
    }
}
