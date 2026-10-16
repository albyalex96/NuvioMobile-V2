package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortBluetoothA2DP
import platform.AVFAudio.AVAudioSessionPortBluetoothHFP
import platform.AVFAudio.AVAudioSessionPortBluetoothLE
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

@Composable
internal actual fun rememberAudioOutputRoute(): AudioOutputRoute? {
    var route by remember { mutableStateOf(detectIosAudioOutputRoute()) }

    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            val detected = detectIosAudioOutputRoute()
            if (detected != route) {
                Logger.i(AUDIO_DELAY_LOG_TAG) {
                    "Audio output changed: ${route?.key ?: "none"} -> ${detected?.key ?: "none"}"
                }
                route = detected
            }
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }
    return route
}

private fun detectIosAudioOutputRoute(): AudioOutputRoute? {
    val output = AVAudioSession.sharedInstance().currentRoute.outputs
        .firstOrNull() as? AVAudioSessionPortDescription
        ?: return null
    val portType = output.portType
    val label = output.portName.trim().ifBlank { portType }
    val normalizedLabel = label
        .lowercase()
        .replace(Regex("\\s+"), "_")
        .replace(Regex("[^a-z0-9._:-]"), "")
        .ifBlank { portType.lowercase() }
    return AudioOutputRoute(
        key = "type:${portType.lowercase()}|name:$normalizedLabel",
        label = label,
        isBluetooth = portType == AVAudioSessionPortBluetoothA2DP ||
            portType == AVAudioSessionPortBluetoothLE ||
            portType == AVAudioSessionPortBluetoothHFP,
    )
}
