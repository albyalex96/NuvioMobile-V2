package com.nuvio.app.features.player

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import java.util.Locale

/** Debounce window so flapping add/remove events coalesce into one route decision. */
private const val AUDIO_ROUTE_CHANGE_DEBOUNCE_MS = 700L

@Composable
internal actual fun rememberAudioOutputRoute(): AudioOutputRoute? {
    val context = LocalContext.current.applicationContext
    var route by remember { mutableStateOf(AudioOutputRouteDetector.detect(context)) }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val handler = Handler(Looper.getMainLooper())
        val reprobe = Runnable {
            val detected = AudioOutputRouteDetector.detect(context)
            if (detected != route) {
                Logger.i(AUDIO_DELAY_LOG_TAG) {
                    "Audio output changed: ${route?.key ?: "none"} -> ${detected?.key ?: "none"}"
                }
                route = detected
            }
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = schedule()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = schedule()

            private fun schedule() {
                handler.removeCallbacks(reprobe)
                handler.postDelayed(reprobe, AUDIO_ROUTE_CHANGE_DEBOUNCE_MS)
            }
        }
        runCatching { audioManager?.registerAudioDeviceCallback(callback, handler) }
            .onFailure { Logger.w(AUDIO_DELAY_LOG_TAG, it) { "Failed to register audio route callback" } }
        onDispose {
            handler.removeCallbacks(reprobe)
            runCatching { audioManager?.unregisterAudioDeviceCallback(callback) }
        }
    }
    return route
}

/**
 * Detects the active (or preferred) media audio output (ported from NuvioTV). On API 33+ the
 * device actually used for media is asked to the system, so a Bluetooth remote that shows up
 * in the device list does not steal the route from HDMI; older versions rank the outputs.
 */
internal object AudioOutputRouteDetector {
    fun detect(context: Context): AudioOutputRoute? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return null
        detectMediaRoutedDevice(audioManager)?.let { return it.toRoute() }
        val device = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }
            .getOrNull()
            ?.minByOrNull { routeRank(it.type) }
            ?.takeIf { routeRank(it.type) < ROUTE_RANK_UNSUPPORTED }
        return device?.toRoute()
    }

    @SuppressLint("NewApi")
    private fun detectMediaRoutedDevice(audioManager: AudioManager): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching {
            val attributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
            audioManager.getAudioDevicesForAttributes(attributes).firstOrNull()
        }.getOrNull()
    }

    private fun AudioDeviceInfo.toRoute(): AudioOutputRoute {
        val typeLabel = typeName(type)
        val product = productName?.toString()?.trim().orEmpty()
        val label = product.ifBlank { typeLabel }
        val normalizedLabel = label
            .lowercase(Locale.US)
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9._:-]"), "")
            .ifBlank { typeLabel.lowercase(Locale.US) }
        return AudioOutputRoute(
            key = "type:${typeLabel.lowercase(Locale.US)}|name:$normalizedLabel",
            label = label,
            isBluetooth = isBluetoothType(type),
        )
    }

    @SuppressLint("InlinedApi")
    private fun routeRank(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
        AudioDeviceInfo.TYPE_BLE_HEADSET -> 1
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> 2
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> 3
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 4
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> 10
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> 20
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 30
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 40
        else -> ROUTE_RANK_UNSUPPORTED
    }

    @SuppressLint("InlinedApi")
    private fun isBluetoothType(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> true
        else -> false
    }

    @SuppressLint("InlinedApi")
    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth_a2dp"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth_sco"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "ble_headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "ble_speaker"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "ble_broadcast"
        AudioDeviceInfo.TYPE_HDMI -> "hdmi"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "hdmi_arc"
        AudioDeviceInfo.TYPE_HDMI_EARC -> "hdmi_earc"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "usb_device"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb_accessory"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "built_in_speaker"
        else -> "type_$type"
    }

    private const val ROUTE_RANK_UNSUPPORTED = 100
}
