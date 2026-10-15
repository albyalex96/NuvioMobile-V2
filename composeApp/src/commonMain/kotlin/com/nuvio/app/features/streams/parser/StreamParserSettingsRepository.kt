package com.nuvio.app.features.streams.parser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

data class StreamParserSettingsUiState(
    val enabled: Boolean = false,
    val animationsEnabled: Boolean = true,
)

object StreamParserSettingsRepository {
    private val _uiState = MutableStateFlow(StreamParserSettingsUiState())
    val uiState: StateFlow<StreamParserSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun clearLocalState() {
        hasLoaded = false
        _uiState.value = StreamParserSettingsUiState()
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.enabled == enabled) return
        _uiState.value = _uiState.value.copy(enabled = enabled)
        StreamParserSettingsStorage.saveEnabled(enabled)
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.animationsEnabled == enabled) return
        _uiState.value = _uiState.value.copy(animationsEnabled = enabled)
        StreamParserSettingsStorage.saveAnimationsEnabled(enabled)
    }

    /** Sync hooks: the keys travel inside the existing `stream_badge_settings` payload. */
    fun exportToSyncPayload(): JsonObject = StreamParserSettingsStorage.exportToSyncPayload()

    fun replaceFromSyncPayload(payload: JsonObject) {
        StreamParserSettingsStorage.replaceFromSyncPayload(payload)
        onProfileChanged()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        _uiState.value = StreamParserSettingsUiState(
            enabled = StreamParserSettingsStorage.loadEnabled() ?: false,
            animationsEnabled = StreamParserSettingsStorage.loadAnimationsEnabled() ?: true,
        )
    }
}
