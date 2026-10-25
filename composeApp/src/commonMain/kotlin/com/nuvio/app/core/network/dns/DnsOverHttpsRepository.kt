package com.nuvio.app.core.network.dns

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Local storage of the DNS options (device level, not per profile). */
internal expect object DnsOverHttpsStorage {
    fun loadSettings(): String?
    fun saveSettings(json: String)
    fun loadSyncEnabled(): Boolean?
    fun saveSyncEnabled(enabled: Boolean)
}

/** Where the app can route its own lookups through DoH (Android OkHttp clients). */
internal expect object DnsOverHttpsPlatform {
    val isSupported: Boolean

    /** Resolves [DNS_OVER_HTTPS_TEST_HOST] with [settings] only, without falling back to the device DNS. */
    suspend fun test(settings: DnsOverHttpsSettings): DnsOverHttpsTestResult
}

internal const val DNS_OVER_HTTPS_TEST_HOST = "example.com"

sealed interface DnsOverHttpsTestResult {
    data class Success(val addresses: List<String>, val elapsedMs: Long) : DnsOverHttpsTestResult
    data class Failure(val message: String) : DnsOverHttpsTestResult
}

/**
 * DNS over HTTPS options (Settings → Advanced → Network). They belong to the device: they travel
 * in the profile blob (`stream_badge_settings` payload) only while "Sync across devices" is on.
 * The sync preference itself always travels, like the MAL login sync, so turning it on or off on
 * one device does the same on the others. Blobs without the keys (older builds) keep the local
 * values.
 */
object DnsOverHttpsRepository {
    private const val SYNC_KEY = "dns_over_https_sync"
    private const val SETTINGS_KEY = "dns_over_https_settings"

    private val log = Logger.withTag("DnsOverHttps")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = SynchronizedObject()

    private val _settings = MutableStateFlow(DnsOverHttpsSettings())
    val settings: StateFlow<DnsOverHttpsSettings> = _settings.asStateFlow()

    private val _syncAcrossDevices = MutableStateFlow(false)
    val syncAcrossDevices: StateFlow<Boolean> = _syncAcrossDevices.asStateFlow()

    private var hasLoaded = false

    /** Called at app start, before the first network request, so lookups see the saved resolver. */
    fun ensureLoaded() {
        synchronized(lock) {
            if (hasLoaded) return
            hasLoaded = true
            _settings.value = DnsOverHttpsStorage.loadSettings()
                ?.let(::decodeSettings)
                ?: DnsOverHttpsSettings()
            _syncAcrossDevices.value = DnsOverHttpsStorage.loadSyncEnabled() ?: false
        }
        log.i { "Loaded: ${_settings.value.describeForLog()} sync=${_syncAcrossDevices.value}" }
    }

    fun update(transform: (DnsOverHttpsSettings) -> DnsOverHttpsSettings) {
        ensureLoaded()
        val next = synchronized(lock) {
            val next = transform(_settings.value).normalized()
            if (next == _settings.value) return
            _settings.value = next
            next
        }
        DnsOverHttpsStorage.saveSettings(json.encodeToString(DnsOverHttpsSettings.serializer(), next))
        log.i { "Settings changed: ${next.describeForLog()}" }
    }

    fun setSyncAcrossDevices(enabled: Boolean) {
        ensureLoaded()
        if (_syncAcrossDevices.value == enabled) return
        _syncAcrossDevices.value = enabled
        DnsOverHttpsStorage.saveSyncEnabled(enabled)
        log.i { "Sync across devices ${if (enabled) "enabled" else "disabled"}" }
    }

    /**
     * Platforms without DoH (iOS) write no keys, so the other devices keep their own values
     * instead of reading "sync off" from them.
     */
    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        if (!DnsOverHttpsPlatform.isSupported) return@buildJsonObject
        ensureLoaded()
        val sync = _syncAcrossDevices.value
        put(SYNC_KEY, encodeSyncBoolean(sync))
        if (sync) {
            put(SETTINGS_KEY, encodeSyncString(json.encodeToString(DnsOverHttpsSettings.serializer(), _settings.value)))
        }
    }

    fun applyFromSyncPayload(payload: JsonObject) {
        if (!DnsOverHttpsPlatform.isSupported) return
        val remoteSync = payload.decodeSyncBoolean(SYNC_KEY) ?: return
        setSyncAcrossDevices(remoteSync)
        if (!remoteSync) return
        val remote = payload.decodeSyncString(SETTINGS_KEY)?.let(::decodeSettings) ?: return
        if (remote != _settings.value) {
            log.i { "Applying synced settings: ${remote.describeForLog()}" }
            update { remote }
        }
    }

    /** Changes that must push the profile blob. */
    fun syncSignature(settings: DnsOverHttpsSettings, syncEnabled: Boolean): String =
        if (syncEnabled) "doh_sync_${settings.hashCode()}" else "doh_local"

    private fun decodeSettings(value: String): DnsOverHttpsSettings? =
        runCatching { json.decodeFromString(DnsOverHttpsSettings.serializer(), value).normalized() }
            .onFailure { error -> log.w(error) { "Invalid DNS over HTTPS settings ignored" } }
            .getOrNull()
}

/** Never logs the custom address in full: only the provider and whether the options are set. */
internal fun DnsOverHttpsSettings.describeForLog(): String {
    val type = providerType
    val custom = if (type == DnsOverHttpsProvider.Custom) {
        val valid = parseDnsOverHttpsAddress(customAddress) is DohAddressParseResult.Valid
        " customValid=$valid customBootstrap=${customBootstrap.isNotEmpty()}"
    } else {
        ""
    }
    return "provider=${type.id}$custom fallback=$fallbackToSystem"
}
