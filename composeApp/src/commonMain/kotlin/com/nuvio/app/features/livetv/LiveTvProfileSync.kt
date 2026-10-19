package com.nuvio.app.features.livetv

import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Profile sync for Live TV, carried inside the existing `stream_badge_settings` payload of the
 * profile settings blob (schema unchanged).
 *
 * Synced: URL playlists, XMLTV guide sources, favourite channels, the navigation toggle, the
 * guide auto-detect toggle and the channel preferences (hidden channels/categories and order). Never synced: local M3U files (their content can be large) and the
 * Xtream/Stalker provider settings, which hold credentials and stay on the device.
 */
internal object LiveTvProfileSync {
    private const val URL_PLAYLISTS_KEY = "live_tv_url_playlists"
    private const val EPG_SOURCES_KEY = "live_tv_epg_sources"
    private const val FAVORITE_CHANNEL_IDS_KEY = "live_tv_favorite_channel_ids"
    private const val NAVIGATION_ENABLED_KEY = "live_tv_navigation_enabled"
    private const val EPG_AUTO_DETECT_KEY = "live_tv_epg_auto_detect_enabled"
    private const val CHANNEL_PREFERENCES_KEY = "live_tv_channel_preferences"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class SyncedPlaylist(
        val id: String,
        val name: String,
        val url: String,
        val enabled: Boolean = true,
    )

    @Serializable
    private data class SyncedEpgSource(
        val id: String,
        val name: String,
        val url: String,
        val enabled: Boolean = true,
    )

    /** Signature of the synced fields only, so channel/EPG loading does not trigger pushes. */
    fun syncSignature(state: LiveTvUiState): String = listOf(
        state.playlists.filter { it.type == LiveTvPlaylistType.Url }
            .joinToString("|") { "${it.id}:${it.name}:${it.source}:${it.isEnabled}" },
        state.epgSources.joinToString("|") { "${it.id}:${it.name}:${it.url}:${it.isEnabled}" },
        state.favoriteChannelIds.sorted().joinToString("|"),
        state.isNavigationEnabled.toString(),
        state.isEpgAutoDetectEnabled.toString(),
        state.channelPreferences.signature(),
    ).joinToString("#")

    fun exportToSyncPayload(): JsonObject = buildJsonObject {
        LiveTvRepository.ensureLoaded()
        val state = LiveTvRepository.uiState.value
        val playlists = state.playlists
            .filter { it.type == LiveTvPlaylistType.Url }
            .map { SyncedPlaylist(id = it.id, name = it.name, url = it.source, enabled = it.isEnabled) }
        val epgSources = state.epgSources
            .map { SyncedEpgSource(id = it.id, name = it.name, url = it.url, enabled = it.isEnabled) }
        put(URL_PLAYLISTS_KEY, encodeSyncString(json.encodeToString(ListSerializer(SyncedPlaylist.serializer()), playlists)))
        put(EPG_SOURCES_KEY, encodeSyncString(json.encodeToString(ListSerializer(SyncedEpgSource.serializer()), epgSources)))
        put(
            FAVORITE_CHANNEL_IDS_KEY,
            encodeSyncString(json.encodeToString(ListSerializer(String.serializer()), state.favoriteChannelIds.sorted())),
        )
        put(NAVIGATION_ENABLED_KEY, encodeSyncBoolean(state.isNavigationEnabled))
        put(EPG_AUTO_DETECT_KEY, encodeSyncBoolean(state.isEpgAutoDetectEnabled))
        put(CHANNEL_PREFERENCES_KEY, encodeSyncString(encodeLiveTvChannelPreferences(state.channelPreferences)))
    }

    /** Applies only the values present, so blobs from older clients keep the local choices. */
    fun applyFromSyncPayload(payload: JsonObject) {
        val playlists = payload.decodeSyncString(URL_PLAYLISTS_KEY)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(SyncedPlaylist.serializer()), raw) }.getOrNull()
        }?.map { playlist ->
            LiveTvPlaylist(
                id = playlist.id,
                name = playlist.name,
                type = LiveTvPlaylistType.Url,
                source = playlist.url,
                isEnabled = playlist.enabled,
            )
        }
        val epgSources = payload.decodeSyncString(EPG_SOURCES_KEY)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(SyncedEpgSource.serializer()), raw) }.getOrNull()
        }?.map { source ->
            LiveTvEpgSource(id = source.id, name = source.name, url = source.url, isEnabled = source.enabled)
        }
        val favorites = payload.decodeSyncString(FAVORITE_CHANNEL_IDS_KEY)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrNull()
        }?.toSet()
        LiveTvRepository.applySyncedSettings(
            urlPlaylists = playlists,
            epgSources = epgSources,
            favoriteChannelIds = favorites,
            navigationEnabled = payload.decodeSyncBoolean(NAVIGATION_ENABLED_KEY),
            epgAutoDetectEnabled = payload.decodeSyncBoolean(EPG_AUTO_DETECT_KEY),
            channelPreferences = decodeLiveTvChannelPreferences(payload.decodeSyncString(CHANNEL_PREFERENCES_KEY)),
        )
    }
}
