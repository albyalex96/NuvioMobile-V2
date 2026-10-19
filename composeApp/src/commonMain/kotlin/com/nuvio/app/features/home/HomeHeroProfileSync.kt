package com.nuvio.app.features.home

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncStringSet
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncStringSet
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Profile sync of the home hero: "show hero" and the catalogs chosen as hero sources.
 *
 * The home catalog server sync (`SyncHomeCatalogPayload`) leaves the hero out on purpose, so these
 * values were device-local. They now travel inside the `stream_badge_settings` payload of the
 * profile settings blob, like the other local extras, leaving the blob schema as is.
 */
internal object HomeHeroProfileSync {
    private const val HERO_ENABLED_KEY = "home_hero_enabled"
    private const val HERO_SOURCES_KEY = "home_hero_sources"
    private val log = Logger.withTag("HomeHero")

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        val (enabled, sources) = HomeCatalogSettingsRepository.heroSyncState()
        put(HERO_ENABLED_KEY, encodeSyncBoolean(enabled))
        // Unknown catalogs (not loaded yet): keep the profile's list instead of sending a partial one.
        sources?.let { put(HERO_SOURCES_KEY, encodeSyncStringSet(it.toSet())) }
    }

    /** Applies the remote values only when present, so blobs from older clients keep the local choice. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val enabled = payload.decodeSyncBoolean(HERO_ENABLED_KEY)
        val sources = payload.decodeSyncStringSet(HERO_SOURCES_KEY)
        if (enabled == null && sources == null) return
        log.d { "Applying profile hero settings: enabled=$enabled, sources=${sources?.size}" }
        HomeCatalogSettingsRepository.applyHeroFromRemote(enabled, sources)
    }

    /** Changes that must push the profile blob. */
    fun syncSignature(state: HomeCatalogSettingsUiState): String =
        "home_hero_${state.heroEnabled}_" + state.items.filter { it.heroSourceEnabled && !it.isCollection }.joinToString(",") { it.key }
}
