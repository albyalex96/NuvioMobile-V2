package com.nuvio.app.features.streams.parser

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

data class StreamParserSettingsUiState(
    val enabled: Boolean = false,
    val animationsEnabled: Boolean = true,
    /** Independent from [enabled]: also applies to the classic stream card. */
    val sortByQuality: Boolean = false,
    /** Resolutions hidden from the stream results; empty shows everything. */
    val hiddenQualities: Set<StreamParserResolution> = emptySet(),
)

object StreamParserSettingsRepository {
    private val _uiState = MutableStateFlow(StreamParserSettingsUiState())
    val uiState: StateFlow<StreamParserSettingsUiState> = _uiState.asStateFlow()

    private val log = Logger.withTag("StreamQualityFilter")
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

    fun setSortByQuality(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.sortByQuality == enabled) return
        _uiState.value = _uiState.value.copy(sortByQuality = enabled)
        StreamParserSettingsStorage.saveSortByQuality(enabled)
    }

    fun setQualityVisible(quality: StreamParserResolution, visible: Boolean) {
        ensureLoaded()
        val current = _uiState.value.hiddenQualities
        val updated = if (visible) current - quality else current + quality
        if (updated == current) return
        _uiState.value = _uiState.value.copy(hiddenQualities = updated)
        StreamParserSettingsStorage.saveHiddenQualities(updated.mapTo(mutableSetOf()) { it.name })
        log.i { "Quality ${quality.label} ${if (visible) "shown" else "hidden"}; hidden=${updated.map { it.label }}" }
    }

    fun showAllQualities() {
        ensureLoaded()
        if (_uiState.value.hiddenQualities.isEmpty()) return
        _uiState.value = _uiState.value.copy(hiddenQualities = emptySet())
        StreamParserSettingsStorage.saveHiddenQualities(emptySet())
        log.i { "All qualities shown" }
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
            sortByQuality = StreamParserSettingsStorage.loadSortByQuality() ?: false,
            hiddenQualities = decodeHiddenQualities(StreamParserSettingsStorage.loadHiddenQualities()),
        )
    }

    private fun decodeHiddenQualities(names: Set<String>?): Set<StreamParserResolution> {
        if (names.isNullOrEmpty()) return emptySet()
        val qualities = StreamParserResolution.entries.filterTo(mutableSetOf()) { it.name in names }
        if (qualities.size != names.size) {
            log.w { "Ignored unknown hidden qualities: ${names - qualities.map { it.name }.toSet()}" }
        }
        return qualities
    }
}
