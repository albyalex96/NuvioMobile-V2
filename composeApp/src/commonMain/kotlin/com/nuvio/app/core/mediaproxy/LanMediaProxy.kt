package com.nuvio.app.core.mediaproxy

import kotlinx.coroutines.flow.StateFlow

/** What the proxy serves for one registration. */
sealed interface MediaProxySource {
    /**
     * An HTTP(S) link, fetched by this device with [requestHeaders]. [responseHeaders] are added
     * to (and override) the upstream response headers, e.g. a corrected Content-Type.
     * Loopback links (P2P engine) are restricted to their own host and port.
     */
    data class Remote(
        val url: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val responseHeaders: Map<String, String> = emptyMap(),
    ) : MediaProxySource

    /** A file on this device: `file://` URI, `content://` URI or absolute path. */
    data class LocalFile(val uri: String) : MediaProxySource
}

enum class MediaProxyScope {
    /** Reachable only from this device (127.0.0.1), e.g. for players that cannot send headers. */
    Loopback,

    /** Reachable from the local network (Wi-Fi, Ethernet, hotspot), e.g. for a cast receiver. */
    Lan,
}

data class MediaProxyOptions(
    val scope: MediaProxyScope = MediaProxyScope.Lan,
    /** Adds permissive CORS headers, needed by web-based players (Cast receivers, browsers). */
    val cors: Boolean = true,
    /**
     * Rewrites HLS playlists and DASH manifests so variants, segments and keys also go through
     * the proxy (with the same headers).
     */
    val rewriteManifests: Boolean = true,
    /** Who registered the source, shown in the logs (e.g. "Cast"). */
    val label: String = "media",
)

/** Live state of the server, shown in the proxy settings page. */
data class MediaProxyStatus(
    val running: Boolean = false,
    val port: Int? = null,
    val activeSources: Int = 0,
    /** Callers of the active registrations (their [MediaProxyOptions.label]). */
    val activeLabels: List<String> = emptyList(),
)

/** A registered source; [url] is what the client should play. Pass it to [LanMediaProxy.unregister]. */
class MediaProxyHandle internal constructor(
    val token: String,
    val url: String,
)

/**
 * Small HTTP server that serves remote links (adding headers), the P2P engine and local files
 * to other players, on this device or on the local network. Each registration gets a random,
 * unguessable token; the server runs only while at least one registration exists. Options in
 * [MediaProxySettingsRepository].
 * Android only for now: on iOS [register] returns null.
 */
expect object LanMediaProxy {
    val isSupported: Boolean

    val status: StateFlow<MediaProxyStatus>

    /**
     * IPv4 address announced to LAN clients: the manual one from [MediaProxySettings] when set,
     * otherwise the detected one; null without Wi-Fi/Ethernet/hotspot.
     */
    fun lanAddress(): String?

    /** Address found on the network interfaces, ignoring the manual setting. */
    fun detectedLanAddress(): String?

    /** Starts serving [source]; null when it cannot be served (e.g. no local network for [MediaProxyScope.Lan]). */
    fun register(source: MediaProxySource, options: MediaProxyOptions = MediaProxyOptions()): MediaProxyHandle?

    fun unregister(handle: MediaProxyHandle)
}
