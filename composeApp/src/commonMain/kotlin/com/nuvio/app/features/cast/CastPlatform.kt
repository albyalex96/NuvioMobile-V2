package com.nuvio.app.features.cast

import kotlinx.coroutines.flow.StateFlow

/**
 * Cast session and device discovery. Android uses the Google Cast SDK with the Default Media
 * Receiver; iOS is not supported yet and always reports [CastSessionState.Unavailable].
 */
internal expect object CastPlatform {
    val state: StateFlow<CastSessionState>

    /** Devices found while discovery is running. */
    val devices: StateFlow<List<CastDevice>>

    /** Starts the active scan used by the device picker; pair every call with [stopDiscovery]. */
    fun startDiscovery()

    fun stopDiscovery()

    fun selectDevice(deviceId: String)

    /** Ends the session and stops the receiver app. */
    fun disconnect()

    /**
     * This device can serve streams to the receiver through its LAN proxy (it has an address on
     * a Wi-Fi/Ethernet network or runs a hotspot). Needed for P2P streams, downloads and links
     * with headers.
     */
    fun canServeLocalMedia(): Boolean
}
