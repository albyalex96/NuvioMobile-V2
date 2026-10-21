package com.nuvio.app.features.player

import com.nuvio.app.features.opensubtitles.OPENSUBTITLES_PROVIDER_NAME
import com.nuvio.app.features.opensubtitles.OpenSubtitlesSubtitleSource
import com.nuvio.app.features.subdl.SUBDL_PROVIDER_NAME
import com.nuvio.app.features.subdl.SubDlSubtitleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_player_no_subtitles_found
import org.jetbrains.compose.resources.getString

object SubtitleRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _addonSubtitles = MutableStateFlow<List<AddonSubtitle>>(emptyList())
    val addonSubtitles: StateFlow<List<AddonSubtitle>> = _addonSubtitles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow<SubtitleLoadingProgress?>(null)
    internal val loadingProgress: StateFlow<SubtitleLoadingProgress?> = _loadingProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var activeFetchJob: Job? = null

    fun fetchAddonSubtitles(type: String, videoId: String) {
        activeFetchJob?.cancel()
        _loadingProgress.value = null
        activeFetchJob = scope.launch {
            _isLoading.value = true
            _error.value = null
            _addonSubtitles.value = emptyList()

            val requests = addonSubtitleRequests(type, videoId)
            val includeOpenSubtitles = OpenSubtitlesSubtitleSource.isActiveFor(type, videoId)
            val includeSubDl = SubDlSubtitleSource.isActiveFor(type, videoId)
            if (requests.isEmpty() && !includeOpenSubtitles && !includeSubDl) {
                _isLoading.value = false
                return@launch
            }

            val total = requests.size + (if (includeOpenSubtitles) 1 else 0) + (if (includeSubDl) 1 else 0)
            _loadingProgress.value = SubtitleLoadingProgress(total = total)
            coroutineScope {
                if (includeOpenSubtitles) {
                    launch {
                        val subtitles = OpenSubtitlesSubtitleSource.fetch(type, videoId)
                        // Integration results always come after the addon ones, in source order.
                        _addonSubtitles.update { (it + subtitles).sortedBy { subtitle -> subtitle.source.ordinal } }
                        _loadingProgress.update { progress ->
                            progress?.copy(completed = progress.completed + 1, addonName = OPENSUBTITLES_PROVIDER_NAME)
                        }
                    }
                }
                if (includeSubDl) {
                    launch {
                        val subtitles = SubDlSubtitleSource.fetch(type, videoId)
                        _addonSubtitles.update { (it + subtitles).sortedBy { subtitle -> subtitle.source.ordinal } }
                        _loadingProgress.update { progress ->
                            progress?.copy(completed = progress.completed + 1, addonName = SUBDL_PROVIDER_NAME)
                        }
                    }
                }
                if (requests.isNotEmpty()) {
                    loadAddonSubtitles(requests) { request, subtitles ->
                        _addonSubtitles.update { current ->
                            val (integrations, addons) = current.partition { it.source != AddonSubtitleSource.Addon }
                            addons + subtitles + integrations
                        }
                        _loadingProgress.update { progress ->
                            progress?.copy(completed = progress.completed + 1, addonName = request.addonName)
                        }
                    }
                }
            }

            if (_addonSubtitles.value.isEmpty()) {
                _error.value = getString(Res.string.compose_player_no_subtitles_found)
            }
            _isLoading.value = false
        }
    }

    fun clear() {
        activeFetchJob?.cancel()
        _loadingProgress.value = null
        _addonSubtitles.value = emptyList()
        _isLoading.value = false
        _error.value = null
    }
}

internal data class SubtitleLoadingProgress(
    val total: Int,
    val completed: Int = 0,
    val addonName: String? = null,
)
