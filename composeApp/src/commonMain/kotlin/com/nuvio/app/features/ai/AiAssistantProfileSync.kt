package com.nuvio.app.features.ai

import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Profile sync of all the AI assistant settings, API keys included, carried inside the
 * `stream_badge_settings` payload of the profile settings blob like SubDL: the custom
 * credential RPCs do not work on the server, so the keys travel with the blob.
 */
internal object AiAssistantProfileSync {
    private const val SETTINGS_KEY = "ai_assistant_settings"

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        put(SETTINGS_KEY, encodeSyncString(AiAssistantRepository.encode(AiAssistantRepository.snapshot())))
    }

    /** Applies the remote settings only when present, so blobs from older clients keep the local ones. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val encoded = payload.decodeSyncString(SETTINGS_KEY) ?: return
        val settings = AiAssistantRepository.decode(encoded) ?: return
        AiAssistantRepository.applyRemoteSettings(settings)
    }

    /** Changes that must push the profile blob. */
    fun syncSignature(settings: AiAssistantSettings): String = "ai_assistant_${settings.hashCode()}"
}
