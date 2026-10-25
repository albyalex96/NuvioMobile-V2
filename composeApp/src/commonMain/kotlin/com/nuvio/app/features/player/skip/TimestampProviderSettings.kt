package com.nuvio.app.features.player.skip

import co.touchlab.kermit.Logger
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal expect object TimestampProviderStorage {
    fun loadSettings(): String?
    fun saveSettings(payload: String)
}

/** How SkipDB shifts timestamps recorded on a stream of a different length (`adjust` parameter). */
@Serializable
enum class SkipDbAdjustMode(val apiValue: String) {
    @SerialName("conservative")
    CONSERVATIVE("conservative"),

    @SerialName("greedy")
    GREEDY("greedy"),

    @SerialName("none")
    NONE("none"),
}

@Serializable
data class SkipDbSettings(
    @SerialName("submit_enabled") val submitEnabled: Boolean = false,
    @SerialName("api_key") val apiKey: String = "",
    /** Sends the stream length, so SkipDB can match or shift timestamps to this exact release. */
    @SerialName("send_duration") val sendDuration: Boolean = true,
    /** Drops segments recorded on a stream whose length differs too much to be shifted. */
    @SerialName("ignore_out_of_range") val ignoreOutOfRange: Boolean = false,
    @SerialName("min_confidence_percent") val minConfidencePercent: Int = 0,
    @SerialName("adjust_mode") val adjustMode: SkipDbAdjustMode = SkipDbAdjustMode.CONSERVATIVE,
)

@Serializable
data class TimestampProviderSettings(
    /** Provider IDs in priority order; providers missing from it follow in registry order. */
    val priority: List<String> = emptyList(),
    /** Providers chosen to receive submissions; null until the user chooses (highest-priority ready one). */
    @SerialName("submit_targets") val submitTargets: List<String>? = null,
    /**
     * True: each kind of segment comes from the highest-priority service that has it (intro from one,
     * outro from another). False: only the highest-priority service with any segment is used.
     */
    @SerialName("merge_providers") val mergeProviders: Boolean = true,
    @SerialName("skipdb") val skipDb: SkipDbSettings = SkipDbSettings(),
)

/**
 * Per-profile settings shared by the timestamp integrations (priority order, submission targets)
 * and the settings of the integrations that are not part of the player settings (SkipDB). Saved as a
 * single JSON document and synced through [TimestampIntegrationsProfileSync].
 */
object TimestampProviderSettingsRepository {
    private val log = Logger.withTag("TimestampIntegrations")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _uiState = MutableStateFlow(TimestampProviderSettings())
    val uiState: StateFlow<TimestampProviderSettings> = _uiState.asStateFlow()
    private var loadedProfileId: Int? = null

    /** Loads the active profile's settings; switching profile reloads them on the next access. */
    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        _uiState.value = decodeTimestampProviderSettings(TimestampProviderStorage.loadSettings())
    }

    fun current(): TimestampProviderSettings {
        ensureLoaded()
        return _uiState.value
    }

    fun setPriority(ids: List<String>) {
        if (ids == current().priority) return
        log.i { "Timestamp provider priority set to $ids" }
        save(current().copy(priority = ids))
    }

    fun setSubmitTargets(ids: List<String>) {
        if (ids == current().submitTargets) return
        log.i { "Timestamp submission targets set to $ids" }
        save(current().copy(submitTargets = ids))
    }

    fun setMergeProviders(enabled: Boolean) {
        if (enabled == current().mergeProviders) return
        log.i { "Timestamp merging across providers ${if (enabled) "enabled" else "disabled"}" }
        save(current().copy(mergeProviders = enabled))
    }

    fun updateSkipDb(transform: (SkipDbSettings) -> SkipDbSettings) {
        val updated = transform(current().skipDb).let {
            it.copy(apiKey = it.apiKey.trim(), minConfidencePercent = it.minConfidencePercent.coerceIn(0, 100))
        }
        if (updated == current().skipDb) return
        save(current().copy(skipDb = updated))
    }

    internal fun encodeSkipDb(settings: SkipDbSettings): String =
        json.encodeToString(SkipDbSettings.serializer(), settings)

    internal fun decodeSkipDb(payload: String): SkipDbSettings? =
        runCatching { json.decodeFromString(SkipDbSettings.serializer(), payload) }
            .onFailure { log.w(it) { "Ignoring unreadable SkipDB settings from the profile blob" } }
            .getOrNull()

    private fun save(settings: TimestampProviderSettings) {
        _uiState.value = settings
        TimestampProviderStorage.saveSettings(json.encodeToString(TimestampProviderSettings.serializer(), settings))
    }

    internal fun decodeTimestampProviderSettings(payload: String?): TimestampProviderSettings {
        if (payload.isNullOrBlank()) return TimestampProviderSettings()
        return runCatching { json.decodeFromString(TimestampProviderSettings.serializer(), payload) }
            .onFailure { log.w(it) { "Resetting unreadable timestamp provider settings" } }
            .getOrDefault(TimestampProviderSettings())
    }
}
