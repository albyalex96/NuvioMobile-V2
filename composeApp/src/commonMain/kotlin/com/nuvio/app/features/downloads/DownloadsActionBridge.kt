package com.nuvio.app.features.downloads

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** How a downloaded file is opened. */
enum class DownloadPlaybackMode {
    /** Internal player, or the external one when the user enabled it. */
    Default,

    /** Always the internal player (used for Chromecast: the cast session drives it). */
    Internal,

    ExternalPlayer,
}

sealed interface DownloadsAction {
    data class PlayDownload(
        val item: DownloadItem,
        val fromStart: Boolean = false,
        val mode: DownloadPlaybackMode = DownloadPlaybackMode.Default,
    ) : DownloadsAction

    data class PlayExternalFile(
        val file: ExternalVideoFile,
        val mode: DownloadPlaybackMode = DownloadPlaybackMode.Default,
    ) : DownloadsAction

    data class OpenDetails(val item: DownloadItem) : DownloadsAction
}

/**
 * Lets screens that do not own navigation (the Settings downloads page) ask the app shell to
 * play a file or open a title. Collected in `MainAppContent`.
 */
object DownloadsActionBridge {
    private val _actions = MutableSharedFlow<DownloadsAction>(extraBufferCapacity = 4)
    val actions: SharedFlow<DownloadsAction> = _actions.asSharedFlow()

    fun send(action: DownloadsAction) {
        _actions.tryEmit(action)
    }
}

/** Provider id of players started on a file that is not linked to a title (no progress, no scrobbling). */
const val LOCAL_FILE_PROVIDER_ID = "local-file"
