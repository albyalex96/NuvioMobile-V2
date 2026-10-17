package com.nuvio.app.core.mediaproxy

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncInt
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncInt
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

const val MEDIA_PROXY_AUTO_PORT = 0
val MEDIA_PROXY_PORT_RANGE = 1024..65535
val MEDIA_PROXY_CONNECT_TIMEOUT_RANGE = 5..60
val MEDIA_PROXY_READ_TIMEOUT_RANGE = 10..120

/** Advanced options of [LanMediaProxy] (Settings → Advanced → Media proxy). */
@Serializable
data class MediaProxySettings(
    /** Fixed server port, or [MEDIA_PROXY_AUTO_PORT] for a free one chosen by the system. */
    val port: Int = MEDIA_PROXY_AUTO_PORT,
    /** IPv4 address announced to LAN clients instead of the detected one (multiple interfaces, VPN). */
    val lanAddressOverride: String = "",
    /** Keeps Wi-Fi and the CPU awake while the LAN is being served. */
    val keepAwake: Boolean = true,
    /** User-Agent sent upstream when neither the stream nor the client provides one; blank = built-in. */
    val userAgent: String = "",
    val connectTimeoutSeconds: Int = 15,
    val readTimeoutSeconds: Int = 30,
    /** Global switch for the CORS headers requested by the callers. */
    val corsEnabled: Boolean = true,
    /** Global switch for the HLS/DASH rewriting requested by the callers. */
    val rewriteManifests: Boolean = true,
    /** Cast: every stream goes through the proxy, not only the ones that need it. */
    val castAlwaysProxy: Boolean = false,
    /** Cast: adaptive streams that fail when loaded directly are retried through the proxy. */
    val castProxyFallback: Boolean = true,
) {
    fun normalized(): MediaProxySettings = copy(
        port = port.takeIf { it == MEDIA_PROXY_AUTO_PORT || it in MEDIA_PROXY_PORT_RANGE } ?: MEDIA_PROXY_AUTO_PORT,
        lanAddressOverride = lanAddressOverride.trim().takeIf { it.isEmpty() || isValidIpv4(it) }.orEmpty(),
        userAgent = userAgent.trim().replace(Regex("[\\r\\n]"), ""),
        connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(MEDIA_PROXY_CONNECT_TIMEOUT_RANGE),
        readTimeoutSeconds = readTimeoutSeconds.coerceIn(MEDIA_PROXY_READ_TIMEOUT_RANGE),
    )
}

/** Dotted IPv4 address with four 0..255 parts. */
fun isValidIpv4(value: String): Boolean {
    val parts = value.trim().split('.')
    return parts.size == 4 && parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) && part.toInt() in 0..255
    }
}

/** Local storage of [MediaProxySettings] as a JSON string (also synced with the profile). */
internal expect object MediaProxySettingsStorage {
    fun load(): String?
    fun save(json: String)
}

/**
 * Proxy options. Stored on the device and synced with the profile inside the existing
 * `stream_badge_settings` payload (like the other Enhanced options), leaving the blob schema as
 * is; remote values are applied one by one and only when present, so blobs from older clients
 * keep the local choices.
 */
object MediaProxySettingsRepository {
    private val log = Logger.withTag("LanProxy")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(MediaProxySettings())
    val settings: StateFlow<MediaProxySettings> = _settings.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _settings.value = MediaProxySettingsStorage.load()
            ?.let { stored -> runCatching { json.decodeFromString<MediaProxySettings>(stored) }.getOrNull() }
            ?.normalized()
            ?: MediaProxySettings()
    }

    fun update(transform: (MediaProxySettings) -> MediaProxySettings) {
        ensureLoaded()
        val next = transform(_settings.value).normalized()
        if (next == _settings.value) return
        _settings.value = next
        MediaProxySettingsStorage.save(json.encodeToString(MediaProxySettings.serializer(), next))
        log.i { "Proxy settings changed: ${next.describeForLog()}" }
    }

    fun resetToDefaults() = update { MediaProxySettings() }

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        ensureLoaded()
        val value = _settings.value
        put(PORT_KEY, encodeSyncInt(value.port))
        put(LAN_ADDRESS_KEY, encodeSyncString(value.lanAddressOverride))
        put(KEEP_AWAKE_KEY, encodeSyncBoolean(value.keepAwake))
        put(USER_AGENT_KEY, encodeSyncString(value.userAgent))
        put(CONNECT_TIMEOUT_KEY, encodeSyncInt(value.connectTimeoutSeconds))
        put(READ_TIMEOUT_KEY, encodeSyncInt(value.readTimeoutSeconds))
        put(CORS_KEY, encodeSyncBoolean(value.corsEnabled))
        put(REWRITE_KEY, encodeSyncBoolean(value.rewriteManifests))
        put(CAST_ALWAYS_KEY, encodeSyncBoolean(value.castAlwaysProxy))
        put(CAST_FALLBACK_KEY, encodeSyncBoolean(value.castProxyFallback))
    }

    fun applyFromSyncPayload(payload: JsonObject) {
        update { current ->
            current.copy(
                port = payload.decodeSyncInt(PORT_KEY) ?: current.port,
                lanAddressOverride = payload.decodeSyncString(LAN_ADDRESS_KEY) ?: current.lanAddressOverride,
                keepAwake = payload.decodeSyncBoolean(KEEP_AWAKE_KEY) ?: current.keepAwake,
                userAgent = payload.decodeSyncString(USER_AGENT_KEY) ?: current.userAgent,
                connectTimeoutSeconds = payload.decodeSyncInt(CONNECT_TIMEOUT_KEY) ?: current.connectTimeoutSeconds,
                readTimeoutSeconds = payload.decodeSyncInt(READ_TIMEOUT_KEY) ?: current.readTimeoutSeconds,
                corsEnabled = payload.decodeSyncBoolean(CORS_KEY) ?: current.corsEnabled,
                rewriteManifests = payload.decodeSyncBoolean(REWRITE_KEY) ?: current.rewriteManifests,
                castAlwaysProxy = payload.decodeSyncBoolean(CAST_ALWAYS_KEY) ?: current.castAlwaysProxy,
                castProxyFallback = payload.decodeSyncBoolean(CAST_FALLBACK_KEY) ?: current.castProxyFallback,
            )
        }
    }

    private fun MediaProxySettings.describeForLog(): String =
        "port=${if (port == MEDIA_PROXY_AUTO_PORT) "auto" else port} " +
            "address=${lanAddressOverride.ifEmpty { "auto" }} keepAwake=$keepAwake " +
            "customUserAgent=${userAgent.isNotEmpty()} timeouts=${connectTimeoutSeconds}s/${readTimeoutSeconds}s " +
            "cors=$corsEnabled rewrite=$rewriteManifests castAlways=$castAlwaysProxy castFallback=$castProxyFallback"

    private const val PORT_KEY = "media_proxy_port"
    private const val LAN_ADDRESS_KEY = "media_proxy_lan_address"
    private const val KEEP_AWAKE_KEY = "media_proxy_keep_awake"
    private const val USER_AGENT_KEY = "media_proxy_user_agent"
    private const val CONNECT_TIMEOUT_KEY = "media_proxy_connect_timeout_seconds"
    private const val READ_TIMEOUT_KEY = "media_proxy_read_timeout_seconds"
    private const val CORS_KEY = "media_proxy_cors_enabled"
    private const val REWRITE_KEY = "media_proxy_rewrite_manifests"
    private const val CAST_ALWAYS_KEY = "media_proxy_cast_always"
    private const val CAST_FALLBACK_KEY = "media_proxy_cast_fallback"
}
