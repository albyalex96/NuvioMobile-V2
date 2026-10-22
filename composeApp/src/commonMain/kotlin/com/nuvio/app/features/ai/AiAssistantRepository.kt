package com.nuvio.app.features.ai

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object AiAssistantRepository {
    private val log = Logger.withTag(AI_ASSISTANT_LOG_TAG)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // A provider removed in a future version falls back to the default instead of dropping all the settings.
        coerceInputValues = true
    }

    private val _uiState = MutableStateFlow(AiAssistantSettings())
    val uiState: StateFlow<AiAssistantSettings> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _uiState.value = AiAssistantStorage.loadSettingsPayload()
            ?.let { payload ->
                runCatching { json.decodeFromString<AiAssistantSettings>(payload) }
                    .onFailure { error -> log.w(error) { "Invalid stored AI assistant settings, using defaults" } }
                    .getOrNull()
            }
            ?: AiAssistantSettings()
    }

    fun snapshot(): AiAssistantSettings {
        ensureLoaded()
        return _uiState.value
    }

    fun onProfileChanged() {
        hasLoaded = false
        ensureLoaded()
        val settings = _uiState.value
        log.d { "Profile changed, enabled=${settings.enabled} providers=${settings.providerChain}" }
    }

    /** Account sign-out: the platform cleaner wipes the storage, here only the in-memory state goes. */
    fun clearLocalState() {
        hasLoaded = false
        _uiState.value = AiAssistantSettings()
    }

    fun applyRemoteSettings(settings: AiAssistantSettings) {
        ensureLoaded()
        if (settings == _uiState.value) return
        log.d { "Applying AI assistant settings synced with the profile" }
        update { settings }
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setWebSearchEnabled(enabled: Boolean) = update { it.copy(webSearchEnabled = enabled) }

    fun setProvider(provider: AiProvider) = update { it.copy(provider = provider) }

    fun setTavilyApiKey(apiKey: String) = update { it.copy(tavilyApiKey = apiKey.trim()) }

    fun setApiKey(provider: AiProvider, apiKey: String) = update {
        val value = apiKey.trim()
        when (provider) {
            AiProvider.CEREBRAS -> it.copy(cerebrasApiKey = value)
            AiProvider.GROQ -> it.copy(groqApiKey = value)
            AiProvider.GEMINI -> it.copy(geminiApiKey = value)
            AiProvider.OPENROUTER -> it.copy(openRouterApiKey = value)
        }
    }

    /** A blank [model] (or the provider default) is stored as blank, so the default can follow future updates. */
    fun setModel(provider: AiProvider, model: String) = update {
        val value = model.trim().takeIf { candidate -> candidate != provider.defaultModel }.orEmpty()
        when (provider) {
            AiProvider.CEREBRAS -> it.copy(cerebrasModel = value)
            AiProvider.GROQ -> it.copy(groqModel = value)
            AiProvider.GEMINI -> it.copy(geminiModel = value)
            AiProvider.OPENROUTER -> it.copy(openRouterModel = value)
        }
    }

    private fun update(transform: (AiAssistantSettings) -> AiAssistantSettings) {
        ensureLoaded()
        val updated = transform(_uiState.value)
        if (updated == _uiState.value) return
        _uiState.value = updated
        AiAssistantStorage.saveSettingsPayload(json.encodeToString(updated))
    }

    internal fun encode(settings: AiAssistantSettings): String = json.encodeToString(settings)

    internal fun decode(payload: String): AiAssistantSettings? =
        runCatching { json.decodeFromString<AiAssistantSettings>(payload) }
            .onFailure { error -> log.w(error) { "Invalid AI assistant settings in the profile blob" } }
            .getOrNull()
}
