package com.nuvio.app.features.streams

import kotlinx.coroutines.launch
import com.nuvio.app.core.logging.StartupTrace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.build.AppFeaturePolicy
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.supportsResource
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginsUiState
import com.nuvio.app.features.servers.ServerRepository
import com.nuvio.app.features.servers.ServerStreams

internal fun AddonManifest.supportsStream(type: String, videoId: String): Boolean =
    supportsResource("stream", type, videoId)

internal fun hasCompatiblePlaybackSource(
    addons: List<ManagedAddon>,
    plugins: PluginsUiState,
    type: String,
    videoId: String,
): Boolean = addons.any { it.enabled && it.manifest?.supportsStream(type, videoId) == true } ||
    (plugins.pluginsEnabled && plugins.scrapers.any { it.enabled && it.supportsType(type) })

internal class PlaybackAvailability(
    private val addons: List<ManagedAddon>,
    private val plugins: PluginsUiState,
) {
    fun canStream(type: String, videoId: String): Boolean =
        hasCompatiblePlaybackSource(addons, plugins, type, videoId) ||
            MetaDetailsRepository.findEmbeddedStreams(videoId).isNotEmpty() ||
            ServerStreams.canServe(type, videoId) ||
            CloudStreamStreams.canServe(type, videoId)

    fun canPlay(
        type: String,
        videoId: String,
        parentMetaId: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): Boolean = canStream(type, videoId) || DownloadsRepository.findPlayableDownload(
        parentMetaId = parentMetaId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        videoId = videoId,
    ) != null

    companion object {
        fun current(): PlaybackAvailability = PlaybackAvailability(
            addons = AddonRepository.uiState.value.addons,
            plugins = if (AppFeaturePolicy.pluginsEnabled) {
                PluginRepository.uiState.value
            } else {
                PluginsUiState(pluginsEnabled = false)
            },
        )
    }
}

@Composable
internal fun rememberPlaybackAvailability(): PlaybackAvailability {
    val addons by remember {
        AddonRepository.initialize()
        AddonRepository.uiState
    }.collectAsStateWithLifecycle()
    val plugins = if (AppFeaturePolicy.pluginsEnabled) {
        val state by remember {
            // Off the main thread: on a TV box the stored scrapers take 0.1-4 s to load. The
            // availability is recomputed when the plugin state arrives.
            PluginStartupLoader.loadInBackground()
            PluginRepository.uiState
        }.collectAsStateWithLifecycle()
        state
    } else {
        PluginsUiState(pluginsEnabled = false)
    }
    val downloads by remember {
        // Non-blocking: availability is recomputed when the downloads finish loading.
        DownloadsRepository.preloadInBackground()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    val servers by remember {
        ServerRepository.ensureLoaded()
        ServerRepository.uiState
    }.collectAsStateWithLifecycle()
    return remember(addons, plugins, downloads, servers.revision) {
        PlaybackAvailability(addons.addons, plugins)
    }
}

/** Loads the stored plugins once, on a background thread, for the startup path. */
private object PluginStartupLoader {
    private val started = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun loadInBackground() {
        if (!started.compareAndSet(expect = false, update = true)) return
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
            .launch {
                runCatching {
                    StartupTrace.measure("PluginRepository.initialize (background)") {
                        PluginRepository.initialize()
                    }
                }.onFailure { error ->
                    co.touchlab.kermit.Logger.withTag("Startup").e(error) { "Plugin background load failed" }
                }
            }
    }
}
