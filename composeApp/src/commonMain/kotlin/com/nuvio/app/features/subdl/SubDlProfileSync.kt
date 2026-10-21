package com.nuvio.app.features.subdl

import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Profile sync of all the SubDL settings, API key included, carried inside the
 * `stream_badge_settings` payload of the profile settings blob like OpenSubtitles: the custom
 * credential RPCs do not work on the server, so the key travels with the blob.
 */
internal object SubDlProfileSync {
    private const val SETTINGS_KEY = "subdl_settings"

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        put(SETTINGS_KEY, encodeSyncString(SubDlRepository.encode(SubDlRepository.snapshot())))
    }

    /** Applies the remote settings only when present, so blobs from older clients keep the local ones. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val encoded = payload.decodeSyncString(SETTINGS_KEY) ?: return
        val settings = SubDlRepository.decode(encoded) ?: return
        SubDlRepository.applyRemoteSettings(settings)
    }

    /** Changes that must push the profile blob. */
    fun syncSignature(settings: SubDlSettings): String = "subdl_${settings.hashCode()}"
}
