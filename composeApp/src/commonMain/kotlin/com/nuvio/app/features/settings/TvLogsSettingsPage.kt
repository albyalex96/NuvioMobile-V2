package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.core.logging.AppLogEntry
import com.nuvio.app.core.logging.AppLogStore
import com.nuvio.app.features.tvremote.PhoneTvRemote
import com.nuvio.app.features.tvremote.PhoneTvRemoteClient
import com.nuvio.app.features.tvremote.TvRemoteLogsAction
import com.nuvio.app.features.tvremote.TvRemoteLogsResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.tv_logs_recording_description
import nuvio.composeapp.generated.resources.tv_logs_section_tv
import nuvio.composeapp.generated.resources.tv_logs_status_connected
import nuvio.composeapp.generated.resources.tv_logs_status_loading
import nuvio.composeapp.generated.resources.tv_logs_status_unreachable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val tvLogsLog = Logger.withTag("TvRemoteLogs")
private const val TV_LOGS_POLL_MS = 2_000L
private const val TV_LOGS_RETRY_MS = 4_000L

/** Logs of the paired TV, read through the phone remote; polled while the page is open. */
@Stable
internal class TvAppLogsSource(
    private val client: PhoneTvRemoteClient,
    private val scope: CoroutineScope,
) : AppLogsSource {
    private val _entries = MutableStateFlow<List<AppLogEntry>>(emptyList())
    override val entries: Flow<List<AppLogEntry>> = _entries
    private val _recording = MutableStateFlow(false)
    override val recording: StateFlow<Boolean> = _recording
    private val _supportsSystemImport = MutableStateFlow(false)
    override val supportsSystemImport: StateFlow<Boolean> = _supportsSystemImport
    override val exportFilePrefix: String = "nuvio-tv-log"
    override val recordingDescription: StringResource = Res.string.tv_logs_recording_description

    /** null until the first answer; false while the TV does not answer. */
    val reachable = MutableStateFlow<Boolean?>(null)

    private class PendingAction(
        val action: TvRemoteLogsAction,
        val result: CompletableDeferred<TvRemoteLogsResponse?> = CompletableDeferred(),
    )

    private val actions = Channel<PendingAction>(Channel.UNLIMITED)
    private var lastId = 0L

    override fun setRecording(enabled: Boolean) {
        _recording.value = enabled
        actions.trySend(PendingAction(if (enabled) TvRemoteLogsAction.EnableRecording else TvRemoteLogsAction.DisableRecording))
    }

    override fun clear() {
        _entries.value = emptyList()
        actions.trySend(PendingAction(TvRemoteLogsAction.Clear))
    }

    override suspend fun importSystemLog(): Int? {
        val pending = PendingAction(TvRemoteLogsAction.ImportSystemLog)
        actions.send(pending)
        return pending.result.await()?.imported
    }

    /** Polling loop: runs while the page is composed. */
    suspend fun run() {
        tvLogsLog.i { "Reading the TV logs" }
        var action: PendingAction? = null
        while (true) {
            val response = client.logs(afterId = lastId, action = action?.action)
            action?.result?.complete(response)
            action = null
            if (response == null) {
                if (reachable.value != false) tvLogsLog.w { "TV logs not reachable" }
                reachable.value = false
                action = withTimeoutOrNull(TV_LOGS_RETRY_MS) { actions.receive() }
                continue
            }
            if (reachable.value != true) tvLogsLog.d { "TV logs reachable (newest id ${response.newestId})" }
            reachable.value = true
            _recording.value = response.recording
            _supportsSystemImport.value = response.supportsSystemImport
            val restart = response.reset || response.newestId < lastId
            val merged = if (restart) response.entries else _entries.value + response.entries
            _entries.value = if (restart || response.entries.isNotEmpty()) {
                merged.sortedWith(compareBy<AppLogEntry> { it.epochMs }.thenBy { it.id })
                    .takeLast(AppLogStore.MAX_ENTRIES)
            } else {
                _entries.value
            }
            lastId = if (restart) response.entries.maxOfOrNull { it.id } ?: 0L
            else maxOf(lastId, response.entries.maxOfOrNull { it.id } ?: lastId)
            if (response.hasMore) {
                action = actions.tryReceive().getOrNull()
                continue
            }
            action = withTimeoutOrNull(TV_LOGS_POLL_MS) { actions.receive() }
        }
    }
}

/** State of the "TV logs" page: null when the phone remote is not available. */
@Composable
internal fun rememberTvLogsViewer(): AppLogsViewerState? {
    val client = PhoneTvRemote.activeClient ?: return null
    val scope = rememberCoroutineScope()
    val source = remember(client) { TvAppLogsSource(client, scope) }
    LaunchedEffect(source) { source.run() }
    return rememberAppLogsViewerState(source)
}

/** "TV logs" page (Settings → TV remote): the logs of the paired TV, with the same tools of "App logs". */
internal fun LazyListScope.tvLogsSettingsContent(
    isTablet: Boolean,
    viewer: AppLogsViewerState?,
) {
    val source = viewer?.source as? TvAppLogsSource ?: return
    item(key = "tv_logs_status") {
        TvLogsStatusSection(isTablet = isTablet, source = source)
    }
    appLogsSettingsContent(isTablet = isTablet, viewer = viewer)
}

@Composable
private fun TvLogsStatusSection(isTablet: Boolean, source: TvAppLogsSource) {
    val reachable by source.reachable.collectAsStateWithLifecycle()
    val tvName = PhoneTvRemote.activeClient?.state?.collectAsStateWithLifecycle()?.value?.pairedTv?.name.orEmpty()
    SettingsSection(title = stringResource(Res.string.tv_logs_section_tv), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = tvName,
                description = stringResource(
                    when (reachable) {
                        null -> Res.string.tv_logs_status_loading
                        true -> Res.string.tv_logs_status_connected
                        false -> Res.string.tv_logs_status_unreachable
                    },
                ),
                isTablet = isTablet,
                onClick = {},
            )
        }
    }
}
