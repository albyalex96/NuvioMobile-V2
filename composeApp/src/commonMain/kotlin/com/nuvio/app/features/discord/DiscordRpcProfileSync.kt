package com.nuvio.app.features.discord

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Profile sync of the Discord Rich Presence preferences (all of [DiscordRpcSettings], Live TV
 * toggle included), carried inside the `stream_badge_settings` payload of the profile settings
 * blob like the other local extras. The token is NOT here: it goes through [DiscordCredentialSync].
 */
internal object DiscordRpcProfileSync {
    private const val SETTINGS_KEY = "discord_rpc_settings"
    private val log = Logger.withTag(DISCORD_RPC_LOG_TAG)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        DiscordRpcRepository.ensureLoaded()
        put(SETTINGS_KEY, encodeSyncString(json.encodeToString(DiscordRpcRepository.uiState.value.settings)))
    }

    /** Applies the remote settings only when present, so blobs from older clients keep the local ones. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val encoded = payload.decodeSyncString(SETTINGS_KEY) ?: return
        val settings = runCatching { json.decodeFromString<DiscordRpcSettings>(encoded) }
            .onFailure { error -> log.w(error) { "Invalid Discord settings in the profile blob" } }
            .getOrNull() ?: return
        DiscordRpcRepository.applyRemoteSettings(settings)
    }

    /** Changes that must push the profile blob. */
    fun syncSignature(state: DiscordRpcUiState): String = "discord_rpc_${state.settings}"
}
