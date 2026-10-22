package com.nuvio.app.features.home

import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Profile sync of the "Top 10" rows (toggle and chosen catalogs), carried inside the
 * `stream_badge_settings` payload of the profile settings blob like the other local extras,
 * leaving the blob schema as is.
 */
internal object Top10ProfileSync {
    private const val SETTINGS_KEY = "home_top10_settings"

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        put(SETTINGS_KEY, encodeSyncString(Top10Repository.encode(Top10Repository.snapshot())))
    }

    /** Applies the remote settings only when present, so blobs from older clients keep the local ones. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val encoded = payload.decodeSyncString(SETTINGS_KEY) ?: return
        val settings = Top10Repository.decode(encoded) ?: return
        Top10Repository.applyRemoteSettings(settings)
    }

    /** Changes that must push the profile blob. */
    fun syncSignature(settings: Top10Settings): String = "top10_${settings.hashCode()}"
}
