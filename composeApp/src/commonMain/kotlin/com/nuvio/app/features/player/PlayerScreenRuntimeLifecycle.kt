package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.touchlab.kermit.Logger
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.tv.TvAppRuntime
import kotlin.math.abs

private val lifecycleLog = Logger.withTag("PlayerLifecycle")

/** Two "leaving" signals closer than this (pause, stop, screen off) save the position once. */
internal const val PlayerLeaveSaveToleranceMs = 1_000L

/**
 * Remembers the last position saved while the app was leaving the foreground, so the
 * pause/stop/screen-off signals that arrive together save it only once.
 */
internal class PlayerLeaveSaveTracker {
    private var lastSavedPositionMs: Long? = null

    fun shouldSave(positionMs: Long): Boolean {
        val last = lastSavedPositionMs
        if (last != null && abs(positionMs - last) < PlayerLeaveSaveToleranceMs) return false
        lastSavedPositionMs = positionMs
        return true
    }

    fun markSaved(positionMs: Long) {
        lastSavedPositionMs = positionMs
    }

    fun reset() {
        lastSavedPositionMs = null
    }
}

private enum class PlayerLeaveReason(val label: String) {
    Pause("activity paused"),
    Stop("activity stopped"),
    ScreenOff("screen off"),
}

/**
 * Saves the watch progress when the app leaves the foreground (Home, another app, TV
 * standby, screen off). The lifecycle callbacks run immediately, while the effects keyed on
 * the playback snapshot only run again when Compose draws, which stops in the background.
 * On TV the playback is also paused and stays paused when the app comes back.
 */
@Composable
internal fun PlayerScreenRuntime.BindPlayerLifecycleEffects() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val tracker = remember { PlayerLeaveSaveTracker() }
    val keepAliveForUpload = rememberProgressUploadKeepAlive()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> saveProgressOnLeave(PlayerLeaveReason.Pause, tracker, keepAliveForUpload)
                Lifecycle.Event.ON_STOP -> saveProgressOnLeave(PlayerLeaveReason.Stop, tracker, keepAliveForUpload)
                Lifecycle.Event.ON_START -> tracker.reset()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    OnPlayerScreenOff { saveProgressOnLeave(PlayerLeaveReason.ScreenOff, tracker, keepAliveForUpload) }
}

private fun PlayerScreenRuntime.saveProgressOnLeave(
    reason: PlayerLeaveReason,
    tracker: PlayerLeaveSaveTracker,
    keepAliveForUpload: () -> Unit,
) {
    // ON_PAUSE alone also comes from overlays that keep the player visible: only a stop or
    // a screen off pause the TV playback.
    val pausesPlayback = TvAppRuntime.isTvApp && reason != PlayerLeaveReason.Pause
    if (pausesPlayback && (shouldPlay || playbackSnapshot.isPlaying)) {
        shouldPlay = false
        playerController?.pause()
        lifecycleLog.i { "Paused TV playback (${reason.label})" }
    }
    if (!shouldTrackWatchProgress) {
        lifecycleLog.d { "Progress not saved (${reason.label}): progress not tracked for this playback" }
        return
    }
    val snapshot = playbackSnapshot
    if (playerController == null || snapshot.durationMs <= 0L) {
        lifecycleLog.d { "Progress not saved (${reason.label}): nothing loaded yet" }
        return
    }
    val positionMs = snapshot.positionMs.coerceAtLeast(0L)
    if (pausesPlayback) {
        // The pause scrobble goes out now: the snapshot effect would only send it on return.
        tracker.markSaved(positionMs)
        flushWatchProgress(TrackingScrobbleAction.PAUSE)
        lifecycleLog.i { "Saved progress at ${positionMs}ms/${snapshot.durationMs}ms (${reason.label})" }
    } else if (tracker.shouldSave(positionMs)) {
        saveWatchProgressSnapshot()
        lifecycleLog.i { "Saved progress at ${positionMs}ms/${snapshot.durationMs}ms (${reason.label})" }
    } else {
        lifecycleLog.d { "Progress already saved at ${positionMs}ms (${reason.label})" }
    }
    if (pausesPlayback) {
        // Also covers an upload started by the ON_PAUSE save that is still running.
        keepAliveForUpload()
    }
}
