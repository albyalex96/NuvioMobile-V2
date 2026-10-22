package com.nuvio.app.features.ai

/** Per-profile storage (keys scoped with `ProfileScopedKey`) of the AI assistant settings, API keys included. */
internal expect object AiAssistantStorage {
    fun loadSettingsPayload(): String?
    fun saveSettingsPayload(payload: String)
}
