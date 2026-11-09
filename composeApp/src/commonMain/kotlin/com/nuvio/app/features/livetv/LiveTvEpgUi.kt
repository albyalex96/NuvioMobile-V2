package com.nuvio.app.features.livetv

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import nuvio.composeapp.generated.resources.action_back
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import nuvio.composeapp.generated.resources.live_tv_record_button
import nuvio.composeapp.generated.resources.live_tv_record_program
import nuvio.composeapp.generated.resources.live_tv_recordings_action_cancel
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioBottomSheetDivider
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_button_play
import nuvio.composeapp.generated.resources.live_tv_epg_guide_title
import nuvio.composeapp.generated.resources.live_tv_epg_minutes_left
import nuvio.composeapp.generated.resources.live_tv_epg_next
import nuvio.composeapp.generated.resources.live_tv_epg_no_programs
import nuvio.composeapp.generated.resources.live_tv_epg_now
import nuvio.composeapp.generated.resources.live_tv_epg_on_now
import nuvio.composeapp.generated.resources.live_tv_epg_time_range
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun rememberLiveTvEpgNowMillis(): Long {
    var nowMillis by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = Clock.System.now().toEpochMilliseconds()
            delay(30_000L - nowMillis % 30_000L)
        }
    }
    return nowMillis
}

fun formatLiveTvEpgTime(epochMillis: Long): String {
    val dateTime = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}

@Composable
fun liveTvEpgTimeRange(program: LiveTvEpgProgram): String =
    stringResource(
        Res.string.live_tv_epg_time_range,
        formatLiveTvEpgTime(program.startMillis),
        formatLiveTvEpgTime(program.endMillis),
    )

@Composable
fun LiveTvEpgProgressBar(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(3.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(99.dp))
                .background(progressColor),
        )
    }
}

@Composable
internal fun LiveTvEpgNowNext(
    programs: List<LiveTvEpgProgram>,
    nowMillis: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val current = programs.currentProgram(nowMillis)
    val next = programs.nextProgram(nowMillis)
    if (current == null && next == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (current != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.live_tv_epg_now),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textSecondary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LiveTvEpgProgressBar(
                progress = current.progressAt(nowMillis),
                trackColor = tokens.colors.borderSubtle,
                progressColor = tokens.colors.accent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (next != null) {
            Text(
                text = "${stringResource(Res.string.live_tv_epg_next)} ${formatLiveTvEpgTime(next.startMillis)} · ${next.title}",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvEpgGuideSheet(
    channel: LiveTvChannel,
    programs: List<LiveTvEpgProgram>,
    nowMillis: Long,
    /** Null hides the Play button (the guide opened from the player, the channel is already playing). */
    onPlayClick: (() -> Unit)?,
    onDismiss: () -> Unit,
    /**
     * Shows a Back button that takes the focus when the sheet opens (TV, guide opened from the player).
     * The Back key closes the sheet in any case.
     */
    showBackButton: Boolean = false,
    /** Media keys of the remote (play/pause) while the sheet is open; returns true when handled. */
    onMediaKey: ((Key) -> Boolean)? = null,
    /** Manual recording of the channel; null hides the button (recordings not supported). */
    onRecordClick: (() -> Unit)? = null,
    /** Records (or cancels the recording of) one program; null hides the record buttons. */
    onRecordProgram: ((LiveTvEpgProgram) -> Unit)? = null,
    isProgramRecorded: (LiveTvEpgProgram) -> Boolean = { false },
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val upcoming = remember(programs, nowMillis) { programs.upcomingPrograms(nowMillis) }
    val dismiss: () -> Unit = {
        coroutineScope.launch { dismissNuvioBottomSheet(sheetState = sheetState, onDismiss = onDismiss) }
    }

    val backFocusRequester = remember { FocusRequester() }
    if (showBackButton) {
        LaunchedEffect(Unit) {
            // The sheet has its own window: retry until it is attached and can take the focus.
            repeat(10) {
                if (runCatching { backFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(50)
            }
        }
    }

    NuvioModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
    ) {
      Column(
          modifier = Modifier.onPreviewKeyEvent { event ->
              val handler = onMediaKey ?: return@onPreviewKeyEvent false
              if (event.key != Key.MediaPlayPause && event.key != Key.MediaPlay && event.key != Key.MediaPause) {
                  return@onPreviewKeyEvent false
              }
              if (event.type == KeyEventType.KeyDown) handler(event.key) else true
          },
      ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                var backFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = dismiss,
                    modifier = Modifier
                        .focusRequester(backFocusRequester)
                        .onFocusChanged { backFocused = it.isFocused },
                    color = tokens.colors.overlaySelected,
                    shape = tokens.shapes.chip,
                    border = if (backFocused) BorderStroke(2.dp, tokens.colors.focusRing) else null,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        tint = tokens.colors.textPrimary,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tokens.colors.surfaceCard),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = tokens.colors.textMuted,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(Res.string.live_tv_epg_guide_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                )
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRecordClick != null) {
                Surface(
                    onClick = onRecordClick,
                    color = tokens.colors.overlaySelected,
                    shape = tokens.shapes.chip,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FiberManualRecord,
                        contentDescription = stringResource(Res.string.live_tv_record_button),
                        tint = LiveTvLiveRed,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
            }
            if (onPlayClick != null) Surface(
                onClick = onPlayClick,
                color = tokens.colors.overlaySelected,
                shape = tokens.shapes.chip,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = tokens.colors.accent,
                    )
                    Text(
                        text = stringResource(Res.string.live_tv_button_play),
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        NuvioBottomSheetDivider()
        if (upcoming.isEmpty()) {
            Text(
                text = stringResource(Res.string.live_tv_epg_no_programs),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 28.dp,
                ),
            ) {
                items(
                    items = upcoming,
                    key = { program -> "${program.startMillis}:${program.title}" },
                ) { program ->
                    LiveTvEpgProgramRow(
                        program = program,
                        nowMillis = nowMillis,
                        isRecorded = isProgramRecorded(program),
                        onRecordClick = onRecordProgram?.let { record -> { record(program) } },
                    )
                }
            }
        }
      }
    }
}

@Composable
private fun LiveTvEpgProgramRow(
    program: LiveTvEpgProgram,
    nowMillis: Long,
    isRecorded: Boolean = false,
    onRecordClick: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val isLive = program.isAiringAt(nowMillis)
    var expanded by rememberSaveable(program.startMillis, program.title) { mutableStateOf(false) }
    val hasDescription = !program.description.isNullOrBlank()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isLive) tokens.colors.overlaySelected else tokens.colors.surface,
        shape = tokens.shapes.compactCard,
        border = if (isLive) null else BorderStroke(NuvioTokens.Border.thin, tokens.colors.borderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasDescription) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = liveTvEpgTimeRange(program),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLive) tokens.colors.accent else tokens.colors.textMuted,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(tokens.colors.accent.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.live_tv_epg_on_now),
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.colors.accent,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                if (!program.category.isNullOrBlank()) {
                    Text(
                        text = program.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (onRecordClick != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Rounded.FiberManualRecord,
                        contentDescription = stringResource(
                            if (isRecorded) Res.string.live_tv_recordings_action_cancel else Res.string.live_tv_record_program,
                        ),
                        tint = if (isRecorded) LiveTvLiveRed else tokens.colors.textMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onRecordClick)
                            .padding(6.dp)
                            .size(18.dp),
                    )
                }
            }
            Text(
                text = program.title,
                style = MaterialTheme.typography.titleSmall,
                color = tokens.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isLive) {
                val minutesLeft = ((program.endMillis - nowMillis) / 60_000L).coerceAtLeast(1L).toInt()
                LiveTvEpgProgressBar(
                    progress = program.progressAt(nowMillis),
                    trackColor = tokens.colors.borderSubtle,
                    progressColor = tokens.colors.accent,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(Res.string.live_tv_epg_minutes_left, minutesLeft),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                )
            }
            if (hasDescription) {
                Text(
                    text = program.description.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textSecondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
