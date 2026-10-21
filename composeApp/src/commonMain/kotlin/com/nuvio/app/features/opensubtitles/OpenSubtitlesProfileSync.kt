package com.nuvio.app.features.opensubtitles

import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Profile sync of all the OpenSubtitles settings, API key included, carried inside the
 * `stream_badge_settings` payload of the profile settings blob like the other local extras: the
 * server does not accept new providers in the credential RPCs, so the key travels with the blob.
 */
internal object OpenSubtitlesProfileSync {
    private const val SETTINGS_KEY = "opensubtitles_settings"

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        put(SETTINGS_KEY, encodeSyncString(OpenSubtitlesRepository.encode(OpenSubtitlesRepository.snapshot())))
    }

    /** Applies the remote settings only when present, so blobs from older clients keep the local ones. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val encoded = payload.decodeSyncString(SETTINGS_KEY) ?: return
        val settings = OpenSubtitlesRepository.decode(encoded) ?: return
        OpenSubtitlesRepository.applyRemoteSettings(settings)
    }

    /** Changes that must push the profile blob. */
    fun syncSignature(settings: OpenSubtitlesSettings): String = "opensubtitles_${settings.hashCode()}"
}
