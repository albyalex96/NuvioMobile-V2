package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An APK already downloaded to persistent storage, waiting to be installed. */
@Serializable
internal data class PendingUpdateDownload(
    val channel: String,
    val apkPath: String,
    val update: AppUpdate,
)

internal class UpdatePreferences(versionName: String = AppVersionConfig.VERSION_NAME) {
    private val storedChannel = UpdateChannel.fromStoredValue(AppUpdaterPlatform.getUpdateChannel())
    private val _channel = MutableStateFlow(storedChannel ?: UpdateChannel.defaultForVersion(versionName))
    val channel = _channel.asStateFlow()

    private val _autoDownload = MutableStateFlow(AppUpdaterPlatform.isSupported && AppUpdaterPlatform.getAutoDownload())
    val autoDownload = _autoDownload.asStateFlow()

    init {
        if (storedChannel == null && AppUpdaterPlatform.isSupported) {
            AppUpdaterPlatform.setUpdateChannel(_channel.value.storedValue)
        }
    }

    fun setChannel(channel: UpdateChannel) {
        if (_channel.value == channel) return
        AppUpdaterPlatform.setUpdateChannel(channel.storedValue)
        AppUpdaterPlatform.setIgnoredTag(null)
        _channel.value = channel
    }

    fun setAutoDownload(enabled: Boolean) {
        if (_autoDownload.value == enabled) return
        AppUpdaterPlatform.setAutoDownload(enabled)
        _autoDownload.value = enabled
    }

    fun pendingDownload(): PendingUpdateDownload? {
        val raw = AppUpdaterPlatform.getPendingDownload() ?: return null
        return runCatching { json.decodeFromString<PendingUpdateDownload>(raw) }.getOrNull()
    }

    fun setPendingDownload(pending: PendingUpdateDownload?) {
        AppUpdaterPlatform.setPendingDownload(pending?.let(json::encodeToString))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val shared by lazy { UpdatePreferences() }
    }
}
