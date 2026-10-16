package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import co.touchlab.kermit.Logger

private val audioDelayLog = Logger.withTag(AUDIO_DELAY_LOG_TAG)

/**
 * Keeps the engine audio delay in sync with its three sources: the delay saved for the current
 * audio output (when "remember per device" is on), the `behaviorHints.audioDelayMs` of the active
 * stream and the user's tweak in the Audio panel.
 */
@Composable
internal fun PlayerScreenRuntime.BindAudioDelay() {
    val route = rememberAudioOutputRoute()
    val rememberPerDevice = playerSettingsUiState.rememberAudioDelayPerDevice
    SideEffect { audioOutputRoute = route }

    LaunchedEffect(route?.key, rememberPerDevice) {
        if (!rememberPerDevice || route == null) return@LaunchedEffect
        val storedDelayMs = PlayerTrackPreferenceStorage.loadAudioDelayMs(route.key) ?: 0
        audioDelayParts = audioDelayParts.withDevice(storedDelayMs)
        audioDelayLog.i {
            "Applied audio delay ${storedDelayMs}ms for route=${route.key} " +
                "(+${audioDelayParts.streamMs}ms from stream, ${audioDelayParts.sessionAdjustMs}ms manual on stream)"
        }
    }

    val streamDelayMs = activeStream?.behaviorHints?.audioDelayMs ?: 0
    LaunchedEffect(activeStream) {
        // A new stream swaps in its hint and drops the tweak made on the previous one.
        audioDelayParts = audioDelayParts.withStream(streamDelayMs)
        if (streamDelayMs != 0) {
            audioDelayLog.i { "Stream declares audio delay ${streamDelayMs}ms (total ${audioDelayParts.totalMs}ms)" }
        }
    }

    val totalDelayMs = audioDelayParts.totalMs
    LaunchedEffect(playerController, totalDelayMs) {
        val controller = playerController ?: return@LaunchedEffect
        controller.setAudioDelayMs(totalDelayMs)
        audioDelayLog.d { "Engine audio delay set to ${totalDelayMs}ms" }
    }
}

/** Audio panel change: [delayMs] is the new total delay shown to the user. */
internal fun PlayerScreenRuntime.setAudioDelay(delayMs: Int) {
    audioDelayParts = audioDelayParts.withManualTotal(delayMs)
    val route = audioOutputRoute
    val persistableMs = audioDelayParts.persistableDeviceMs
    if (playerSettingsUiState.rememberAudioDelayPerDevice && route != null && persistableMs != null) {
        PlayerTrackPreferenceStorage.saveAudioDelayMs(route.key, persistableMs)
    }
}
