package com.nuvio.app.features.livetv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_channel_menu_copy_link
import nuvio.composeapp.generated.resources.live_tv_channel_menu_show_epg
import nuvio.composeapp.generated.resources.private_viewing_play_action
import nuvio.composeapp.generated.resources.streams_open_external_player
import org.jetbrains.compose.resources.stringResource

internal val liveTvChannelMenuLog = Logger.withTag("LiveTvChannelMenu")

/** Desktop: the right mouse button opens the channel menu (consumed so it does not also play). */
internal fun Modifier.liveTvSecondaryClick(onSecondaryClick: () -> Unit): Modifier =
    pointerInput(onSecondaryClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    liveTvChannelMenuLog.d { "Right click: channel menu" }
                    onSecondaryClick()
                }
            }
        }
    }

/** Options of a channel, opened with a long press (OK held on TV, right click on desktop). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveTvChannelMenuSheet(
    channel: LiveTvChannel,
    onDismiss: () -> Unit,
    onShowEpg: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenExternal: () -> Unit,
    onWatchPrivately: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        liveTvChannelMenuLog.d { "Menu opened for ${channel.name}" }
        // TV: the first option takes the focus (the sheet has its own window, retry until attached).
        repeat(10) {
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    fun dismissAfter(action: () -> Unit) {
        coroutineScope.launch {
            dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            action()
        }
    }

    NuvioModalBottomSheet(
        onDismissRequest = { coroutineScope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) } },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = nuvioSafeBottomPadding(tokens.spacing.screenHorizontal)),
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            NuvioBottomSheetDivider()
            NuvioBottomSheetActionRow(
                icon = Icons.Rounded.Schedule,
                title = stringResource(Res.string.live_tv_channel_menu_show_epg),
                modifier = Modifier.focusRequester(firstFocus),
                onClick = { dismissAfter(onShowEpg) },
            )
            NuvioBottomSheetActionRow(
                icon = Icons.Rounded.ContentCopy,
                title = stringResource(Res.string.live_tv_channel_menu_copy_link),
                onClick = { dismissAfter(onCopyLink) },
            )
            NuvioBottomSheetActionRow(
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                title = stringResource(Res.string.streams_open_external_player),
                onClick = { dismissAfter(onOpenExternal) },
            )
            NuvioBottomSheetActionRow(
                icon = Icons.Rounded.VisibilityOff,
                title = stringResource(Res.string.private_viewing_play_action),
                onClick = { dismissAfter(onWatchPrivately) },
            )
        }
    }
}

/**
 * Programme guide of the channel playing, opened from the player. Playback keeps going: on TV the
 * sheet takes the focus, has a Back button (the Back key closes it too) and passes the play/pause
 * keys of the remote to the player through [onMediaKey].
 */
@Composable
internal fun LiveTvPlayerGuideSheet(
    channel: LiveTvChannel,
    onDismiss: () -> Unit,
    onMediaKey: (Key) -> Boolean,
) {
    val uiState by LiveTvRepository.uiState.collectAsStateWithLifecycle()
    val recordings by remember {
        LiveTvRecordingsRepository.ensureLoaded()
        LiveTvRecordingsRepository.recordings
    }.collectAsStateWithLifecycle()
    val nowMillis = rememberLiveTvEpgNowMillis()
    val coroutineScope = rememberCoroutineScope()
    var manualRecording by remember { mutableStateOf(false) }
    LaunchedEffect(channel.id) { liveTvChannelMenuLog.i { "Player guide opened for ${channel.name}" } }

    if (manualRecording) {
        LiveTvManualRecordingDialog(
            channel = channel,
            onDismiss = {
                manualRecording = false
                onDismiss()
            },
        )
        return
    }
    LiveTvEpgGuideSheet(
        channel = channel,
        programs = uiState.epgPrograms[channel.id].orEmpty(),
        nowMillis = nowMillis,
        onPlayClick = null,
        onDismiss = onDismiss,
        showBackButton = true,
        onMediaKey = onMediaKey,
        onRecordClick = if (LiveTvRecordingsRepository.isSupported) {
            { manualRecording = true }
        } else {
            null
        },
        onRecordProgram = if (LiveTvRecordingsRepository.isSupported) {
            { program -> coroutineScope.launch { toggleLiveTvProgramRecording(channel, program) } }
        } else {
            null
        },
        isProgramRecorded = { program ->
            recordings.any {
                it.channelId == channel.id && it.title == program.title &&
                    (it.status == LiveTvRecordingStatus.Scheduled || it.status == LiveTvRecordingStatus.Recording) &&
                    it.endMillis >= program.endMillis
            }
        },
    )
}
