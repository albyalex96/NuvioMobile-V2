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
 * blob like the other local extras, together with the Discord token (key [TOKEN_KEY]): the custom
 * credential RPCs do not work on the server, so the token travels with the blob as well.
 *
 * Token rules: a web login replaces it, an explicit logout empties it (the other devices of the
 * profile log out when they apply the blob), a token Discord rejects is only dropped on this device
 * (the blob keeps it, and its fingerprint stops it being applied again here). A device that knows
 * no token exports nothing, so the remote one is kept.
 */
internal object DiscordRpcProfileSync {
    private const val SETTINGS_KEY = "discord_rpc_settings"
    private const val TOKEN_KEY = "discord_rpc_token"
    private val log = Logger.withTag(DISCORD_RPC_LOG_TAG)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        DiscordRpcRepository.ensureLoaded()
        put(SETTINGS_KEY, encodeSyncString(json.encodeToString(DiscordRpcRepository.uiState.value.settings)))
        DiscordRpcRepository.profileSyncToken()?.let { put(TOKEN_KEY, encodeSyncString(it)) }
    }

    /** Applies the remote settings only when present, so blobs from older clients keep the local ones. */
    fun applyFromSyncPayload(payload: JsonObject) {
        payload.decodeSyncString(SETTINGS_KEY)?.let { encoded ->
            runCatching { json.decodeFromString<DiscordRpcSettings>(encoded) }
                .onFailure { error -> log.w(error) { "Invalid Discord settings in the profile blob" } }
                .getOrNull()
                ?.let(DiscordRpcRepository::applyRemoteSettings)
        }
        payload.decodeSyncString(TOKEN_KEY)?.let { remoteToken ->
            log.d { "Discord token found in the profile blob (present=${remoteToken.isNotBlank()})" }
            DiscordRpcRepository.applyRemoteToken(remoteToken)
        }
    }

    /** Changes that must push the profile blob (login and logout included; the token itself never enters the signature). */
    fun syncSignature(state: DiscordRpcUiState): String =
        "discord_rpc_${state.settings}_${state.isLoggedIn}_${state.user?.id}"
}
