package com.nuvio.app.features.downloads

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Download options of the settings page that are not about a single download. */
object DownloadsPreferences {
    const val DEFAULT_SEASON_CONCURRENCY = 2
    val SEASON_CONCURRENCY_RANGE = 1..4

    private val log = Logger.withTag("Downloads")
    private var hasLoaded = false

    private val _seasonConcurrency = MutableStateFlow(DEFAULT_SEASON_CONCURRENCY)
    /** Episodes of the season downloads that download at the same time. */
    val seasonConcurrency: StateFlow<Int> = _seasonConcurrency.asStateFlow()

    private val _organizeEpisodeFolders = MutableStateFlow(true)
    /** Save episodes as "Series/Season N/S01E01 - Title.ext" instead of directly in the downloads folder. */
    val organizeEpisodeFolders: StateFlow<Boolean> = _organizeEpisodeFolders.asStateFlow()

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _seasonConcurrency.value = (DownloadsStorage.loadSeasonConcurrency() ?: DEFAULT_SEASON_CONCURRENCY)
            .coerceIn(SEASON_CONCURRENCY_RANGE)
        _organizeEpisodeFolders.value = DownloadsStorage.loadOrganizeEpisodeFolders() ?: true
    }

    fun setSeasonConcurrency(value: Int) {
        ensureLoaded()
        val normalized = value.coerceIn(SEASON_CONCURRENCY_RANGE)
        if (normalized == _seasonConcurrency.value) return
        log.i { "Season downloads at the same time: $normalized" }
        _seasonConcurrency.value = normalized
        DownloadsStorage.saveSeasonConcurrency(normalized)
    }

    fun setOrganizeEpisodeFolders(enabled: Boolean) {
        ensureLoaded()
        if (enabled == _organizeEpisodeFolders.value) return
        log.i { "Episode folders ${if (enabled) "enabled" else "disabled"}" }
        _organizeEpisodeFolders.value = enabled
        DownloadsStorage.saveOrganizeEpisodeFolders(enabled)
    }
}
