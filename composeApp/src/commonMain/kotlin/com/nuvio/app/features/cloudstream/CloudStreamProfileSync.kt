package com.nuvio.app.features.cloudstream

import co.touchlab.kermit.Logger
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Profile sync of CloudStream: repositories, installed plugins with their toggles and the
 * grouping preference, carried inside the `stream_badge_settings` payload of the profile
 * settings blob like the other local extras. Packages are not synced: the receiving Android
 * device downloads and installs them automatically.
 */
internal object CloudStreamProfileSync {
    private const val SETTINGS_KEY = "cloudstream_settings"
    private val log = Logger.withTag("CloudStreamSync")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        val state = currentState() ?: return@buildJsonObject
        put(SETTINGS_KEY, encodeSyncString(json.encodeToString(CloudStreamSyncState.serializer(), state)))
    }

    /** Applies the remote settings only when present, so blobs from older clients keep the local ones. */
    fun applyFromSyncPayload(payload: JsonObject) {
        if (!AppFeaturePolicy.pluginsEnabled) return
        val encoded = payload.decodeSyncString(SETTINGS_KEY) ?: return
        val state = runCatching { json.decodeFromString(CloudStreamSyncState.serializer(), encoded) }
            .onFailure { error -> log.e(error) { "Could not decode the CloudStream sync payload" } }
            .getOrNull() ?: return
        CloudStreamRepository.applySyncState(state)
    }

    /** Changes that must push the profile blob. */
    fun syncSignature(@Suppress("UNUSED_PARAMETER") state: CloudStreamUiState): String =
        "cloudstream_${currentState().hashCode()}"

    private fun currentState(): CloudStreamSyncState? {
        if (!AppFeaturePolicy.pluginsEnabled) return null
        return CloudStreamRepository.exportSyncState()
    }
}
