package com.nuvio.app.features.plugins

import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncBoolean
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Profile sync for the "group plugin streams by repository" preference.
 *
 * The plugin server sync (`sync_push_plugins`) only carries repository URLs, so this flag was
 * device-local. It now travels inside the existing `stream_badge_settings` payload of the
 * profile settings blob (it only affects how streams are grouped), leaving the blob schema as is.
 */
internal object PluginGroupingSync {
    private const val GROUP_STREAMS_BY_REPOSITORY_KEY = "plugins_group_streams_by_repository"

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        if (!AppFeaturePolicy.pluginsEnabled) return@buildJsonObject
        PluginRepository.initialize()
        put(GROUP_STREAMS_BY_REPOSITORY_KEY, encodeSyncBoolean(PluginRepository.uiState.value.groupStreamsByRepository))
    }

    /** Applies the remote value only when present, so blobs from older clients keep the local choice. */
    fun applyFromSyncPayload(payload: JsonObject) {
        if (!AppFeaturePolicy.pluginsEnabled) return
        val enabled = payload.decodeSyncBoolean(GROUP_STREAMS_BY_REPOSITORY_KEY) ?: return
        if (PluginRepository.uiState.value.groupStreamsByRepository != enabled) {
            PluginRepository.setGroupStreamsByRepository(enabled)
        }
    }
}
