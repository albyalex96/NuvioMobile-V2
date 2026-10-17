package com.nuvio.app.features.cast

/** A cast receiver found on the local network. */
data class CastDevice(
    val id: String,
    val name: String,
    val description: String? = null,
    val isConnecting: Boolean = false,
)

sealed interface CastSessionState {
    /** No cast support on this platform/device (iOS for now, TV app, no Google Play services). */
    data object Unavailable : CastSessionState

    /** Cast is available but no session is active. */
    data object Idle : CastSessionState

    data class Connecting(val deviceName: String?) : CastSessionState

    data class Connected(
        val deviceName: String?,
        val sessionId: String,
    ) : CastSessionState
}

val CastSessionState.isAvailable: Boolean
    get() = this != CastSessionState.Unavailable

/** What the receiver needs to play the current stream. */
data class CastMediaRequest(
    val sourceUrl: String,
    val streamType: String? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val requestHeaders: Map<String, String> = emptyMap(),
    val title: String,
    val episodeTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val isLive: Boolean = false,
    /** Stream features many receivers cannot play (see [castCompatibilityIssues]). */
    val compatibilityIssues: List<String> = emptyList(),
)

/**
 * The receiver cannot fetch the stream by itself: it is served by this device (P2P engine,
 * download) or the link needs headers. It then goes through the LAN proxy of this device.
 */
val CastMediaRequest.needsLanProxy: Boolean
    get() = castSourceIssue(sourceUrl) == CastSourceIssue.LocalOnly || requestHeaders.isNotEmpty()

/** Why a stream cannot be sent to the receiver as it is. */
enum class CastSourceIssue {
    /**
     * Served by this device only (P2P engine, downloaded file, loopback proxy): it can be cast
     * only through the LAN proxy, so only while this device is on the receiver's network.
     */
    LocalOnly,

    /** Video and audio come from two separate URLs, the Default Media Receiver plays one. */
    SeparateAudio,
}

private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]", "0.0.0.0")

internal fun castSourceIssue(sourceUrl: String, sourceAudioUrl: String? = null): CastSourceIssue? {
    val trimmed = sourceUrl.trim()
    val scheme = trimmed.substringBefore("://", missingDelimiterValue = "").lowercase()
    if (scheme != "http" && scheme != "https") return CastSourceIssue.LocalOnly
    val host = trimmed
        .substringAfter("://")
        .substringBefore('/')
        .substringBefore('?')
        .substringAfterLast('@')
        .let { authority ->
            if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')
        }
        .lowercase()
    if (host in LOOPBACK_HOSTS || host.startsWith("127.")) return CastSourceIssue.LocalOnly
    if (!sourceAudioUrl.isNullOrBlank()) return CastSourceIssue.SeparateAudio
    return null
}
