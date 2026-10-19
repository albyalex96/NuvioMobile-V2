package com.nuvio.app.features.livetv

data class LiveTvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val streamType: String? = null,
    val stalkerCommand: String? = null,
    val epgId: String? = null,
)

data class LiveTvEpgProgram(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val description: String? = null,
    val category: String? = null,
) {
    fun isAiringAt(timeMillis: Long): Boolean = timeMillis in startMillis until endMillis

    fun progressAt(timeMillis: Long): Float {
        val duration = endMillis - startMillis
        if (duration <= 0L) return 0f
        return ((timeMillis - startMillis).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
}

data class LiveTvEpgSource(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
)

data class LiveTvStalkerSettings(
    val portalUrl: String = "",
    val macAddress: String = "",
    val username: String = "",
    val password: String = "",
    val isEnabled: Boolean = true,
) {
    val isConfigured: Boolean get() = portalUrl.isNotBlank() && macAddress.isNotBlank()
}

data class LiveTvXtreamSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isEnabled: Boolean = true,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

enum class LiveTvPlaylistType {
    Url,
    LocalFile,
}

data class LiveTvPlaylist(
    val id: String,
    val name: String,
    val type: LiveTvPlaylistType,
    val source: String,
    val isEnabled: Boolean = true,
)

data class LiveTvUiState(
    val playlistUrl: String = "",
    val playlists: List<LiveTvPlaylist> = emptyList(),
    val stalkerSettings: LiveTvStalkerSettings = LiveTvStalkerSettings(),
    val xtreamSettings: LiveTvXtreamSettings = LiveTvXtreamSettings(),
    val channels: List<LiveTvChannel> = emptyList(),
    val favoriteChannelIds: Set<String> = emptySet(),
    val lastWatchedChannelId: String? = null,
    val isNavigationEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    /** Names of the playlists/providers that failed in the last refresh (for localized messages). */
    val failedSourceNames: List<String> = emptyList(),
    val epgSources: List<LiveTvEpgSource> = emptyList(),
    val isEpgAutoDetectEnabled: Boolean = true,
    val epgPrograms: Map<String, List<LiveTvEpgProgram>> = emptyMap(),
    val isEpgLoading: Boolean = false,
    val epgErrorMessage: String? = null,
    val failedEpgSourceNames: List<String> = emptyList(),
    val channelPreferences: LiveTvChannelPreferences = LiveTvChannelPreferences(),
) {
    /** All channels in the user's order, hidden ones included (channel management page). */
    val arrangedChannels: List<LiveTvChannel> by lazy { arrangedLiveTvChannelsCached(channels, channelPreferences).arranged }

    /** Channels shown in the Live TV screen and in the player: user order, hidden ones removed. */
    val displayedChannels: List<LiveTvChannel> by lazy { arrangedLiveTvChannelsCached(channels, channelPreferences).displayed }

    val hasPlaylist: Boolean
        get() = playlists.isNotEmpty() || playlistUrl.isNotBlank() ||
            stalkerSettings.isConfigured || xtreamSettings.isConfigured

    val showInNavigation: Boolean
        get() = hasPlaylist && isNavigationEnabled
}

private class LiveTvArrangedChannels(
    val channels: List<LiveTvChannel>,
    val preferences: LiveTvChannelPreferences,
    val arranged: List<LiveTvChannel>,
    val displayed: List<LiveTvChannel>,
)

/**
 * The state is copied on every EPG/loading update: the last arrangement is reused while the
 * channel list instance and the preferences are unchanged, so the lists keep the same identity
 * and `remember` blocks keyed on them are not recomputed.
 */
private var lastArrangedChannels: LiveTvArrangedChannels? = null

private fun arrangedLiveTvChannelsCached(
    channels: List<LiveTvChannel>,
    preferences: LiveTvChannelPreferences,
): LiveTvArrangedChannels {
    lastArrangedChannels
        ?.takeIf { it.channels === channels && it.preferences == preferences }
        ?.let { return it }
    val arranged = arrangeLiveTvChannels(channels, preferences)
    val displayed = if (preferences.hiddenChannelKeys.isEmpty() && preferences.hiddenCategoryKeys.isEmpty()) {
        arranged
    } else {
        arranged.filter(preferences::isVisible)
    }
    return LiveTvArrangedChannels(channels, preferences, arranged, displayed).also { lastArrangedChannels = it }
}
