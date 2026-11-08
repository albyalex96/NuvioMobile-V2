package com.nuvio.app.features.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.components.HomeEmptyStateCard
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

// ---------------------------------------------------------------------------------------------
// Recordings section
// ---------------------------------------------------------------------------------------------

internal fun LazyListScope.liveTvRecordingsList(
    recordings: List<LiveTvRecording>,
    nowMillis: Long,
    showExactAlarmWarning: Boolean,
    onAllowExactAlarms: () -> Unit,
    onPlay: (LiveTvRecording) -> Unit,
) {
    if (showExactAlarmWarning && recordings.any { it.status == LiveTvRecordingStatus.Scheduled }) {
        item(key = "recordings-exact-alarm") {
            HomeEmptyStateCard(
                title = stringResource(Res.string.live_tv_recordings_exact_alarm_title),
                message = stringResource(Res.string.live_tv_recordings_exact_alarm_warning),
                actionLabel = stringResource(Res.string.live_tv_recordings_exact_alarm_action),
                onActionClick = onAllowExactAlarms,
            )
        }
    }
    if (recordings.isEmpty()) {
        item(key = "recordings-empty") {
            HomeEmptyStateCard(
                title = stringResource(Res.string.live_tv_recordings_empty_title),
                message = stringResource(Res.string.live_tv_recordings_empty_message),
            )
        }
        return
    }
    val groups = listOf(
        LiveTvRecordingStatus.Recording to Res.string.live_tv_recordings_group_recording,
        LiveTvRecordingStatus.Scheduled to Res.string.live_tv_recordings_group_scheduled,
        LiveTvRecordingStatus.Completed to Res.string.live_tv_recordings_group_completed,
        LiveTvRecordingStatus.Failed to Res.string.live_tv_recordings_group_failed,
    )
    groups.forEach { (status, titleRes) ->
        val inGroup = recordings.filter {
            it.status == status || (status == LiveTvRecordingStatus.Recording && it.status == LiveTvRecordingStatus.Converting)
        }.let { list ->
            if (status == LiveTvRecordingStatus.Scheduled) list.sortedBy { it.startMillis } else list.sortedByDescending { it.startMillis }
        }
        if (inGroup.isEmpty()) return@forEach
        item(key = "recordings-header-${status.name}") {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.nuvio.colors.textSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        items(inGroup, key = { "recording-${it.id}" }) { recording ->
            LiveTvRecordingCard(recording = recording, nowMillis = nowMillis, onPlay = { onPlay(recording) })
        }
    }
}

@Composable
private fun LiveTvRecordingCard(
    recording: LiveTvRecording,
    nowMillis: Long,
    onPlay: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val isRecording = recording.status == LiveTvRecordingStatus.Recording
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onPlay,
        enabled = recording.status == LiveTvRecordingStatus.Completed,
        color = tokens.colors.surface,
        shape = tokens.shapes.compactCard,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.colors.surfaceCard),
                contentAlignment = Alignment.Center,
            ) {
                if (!recording.channelLogoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = recording.channelLogoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Rounded.Tv, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isRecording) {
                        Icon(
                            Icons.Rounded.FiberManualRecord,
                            contentDescription = null,
                            tint = LiveTvLiveRed.copy(alpha = rememberLiveTvBlinkAlpha(minAlpha = 0.3f, periodMillis = 1_200).value),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${recording.channelName} · ${formatRecordingRange(recording, nowMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = when (recording.status) {
                    LiveTvRecordingStatus.Scheduled ->
                        stringResource(Res.string.live_tv_recordings_starts_at, formatRecordingDay(recording.startMillis, nowMillis))
                    LiveTvRecordingStatus.Recording -> stringResource(
                        Res.string.live_tv_recordings_until,
                        formatRecordingClock(recording.endMillis),
                        formatRecordingSize(recording.sizeBytes),
                    )
                    LiveTvRecordingStatus.Converting -> stringResource(
                        Res.string.live_tv_recording_converting,
                        ((recording.conversionProgress ?: 0f) * 100).toInt(),
                    )
                    LiveTvRecordingStatus.Completed -> formatRecordingSize(recording.sizeBytes)
                    LiveTvRecordingStatus.Failed -> recording.errorMessage.orEmpty()
                }
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (recording.status == LiveTvRecordingStatus.Failed) tokens.colors.danger else tokens.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (recording.status) {
                LiveTvRecordingStatus.Scheduled -> RecordingAction(
                    icon = Icons.Rounded.Close,
                    description = stringResource(Res.string.live_tv_recordings_action_cancel),
                    onClick = { LiveTvRecordingsRepository.stop(recording.id) },
                )
                LiveTvRecordingStatus.Recording -> RecordingAction(
                    icon = Icons.Rounded.Stop,
                    description = stringResource(Res.string.live_tv_recordings_action_stop),
                    onClick = { LiveTvRecordingsRepository.stop(recording.id) },
                )
                LiveTvRecordingStatus.Completed -> {
                    RecordingAction(
                        icon = Icons.Rounded.PlayArrow,
                        description = stringResource(Res.string.live_tv_recordings_action_play),
                        onClick = onPlay,
                    )
                    RecordingAction(
                        icon = Icons.Rounded.Delete,
                        description = stringResource(Res.string.live_tv_recordings_action_delete),
                        onClick = { LiveTvRecordingsRepository.delete(recording.id) },
                    )
                }
                LiveTvRecordingStatus.Converting -> Unit
                LiveTvRecordingStatus.Failed -> RecordingAction(
                    icon = Icons.Rounded.Delete,
                    description = stringResource(Res.string.live_tv_recordings_action_delete),
                    onClick = { LiveTvRecordingsRepository.delete(recording.id) },
                )
            }
        }
    }
}

@Composable
private fun RecordingAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = tokens.colors.textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .size(22.dp),
    )
}

/** "08/11 21:00–23:10" (the day only when it's not today). */
private fun formatRecordingRange(recording: LiveTvRecording, nowMillis: Long): String =
    "${formatRecordingDay(recording.startMillis, nowMillis)}–${formatRecordingClock(recording.endMillis)}"

private fun formatRecordingDay(epochMillis: Long, nowMillis: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).date
    val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
    val clock = formatRecordingClock(epochMillis)
    return if (date == today) clock else
        "${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthNumber.toString().padStart(2, '0')} $clock"
}

// ---------------------------------------------------------------------------------------------
// Scheduling helpers used by the guide, the channel sheet and the Sports section
// ---------------------------------------------------------------------------------------------

/** Schedules [program] (or cancels its recording) and tells the user. */
internal suspend fun toggleLiveTvProgramRecording(channel: LiveTvChannel, program: LiveTvEpgProgram) {
    val existing = LiveTvRecordingsRepository.findProgram(channel.id, program)
    if (existing != null) {
        LiveTvRecordingsRepository.stop(existing.id)
        return
    }
    LiveTvRecordingsRepository.scheduleProgram(channel, program)?.let {
        NuvioToastController.show(getString(Res.string.live_tv_record_scheduled_toast, it.title))
    }
}

/**
 * Manual recording of [channel]: now for a chosen length, or on a day between two times.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiveTvManualRecordingDialog(
    channel: LiveTvChannel,
    onDismiss: () -> Unit,
) {
    val zone = TimeZone.currentSystemDefault()
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val today = remember { Instant.fromEpochMilliseconds(now).toLocalDateTime(zone) }
    var scheduleMode by remember { mutableStateOf(false) }
    var durationMinutes by remember { mutableStateOf(60) }
    var dayOffset by remember { mutableStateOf(0) }
    // Minutes of the day; − / + buttons instead of a text field, usable with a TV remote too.
    var startMinutes by remember { mutableStateOf(((today.hour + 1) % 24) * 60) }
    var endMinutes by remember { mutableStateOf(((today.hour + 2) % 24) * 60) }
    val tokens = MaterialTheme.nuvio
    val scheduledToast = stringResource(Res.string.live_tv_record_scheduled_toast, channel.name)

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.live_tv_record_dialog_title, channel.name),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(Res.string.live_tv_record_mode_now), selected = !scheduleMode, onClick = { scheduleMode = false })
                Chip(stringResource(Res.string.live_tv_record_mode_schedule), selected = scheduleMode, onClick = { scheduleMode = true })
            }
            if (!scheduleMode) {
                RecordingDialogLabel(stringResource(Res.string.live_tv_record_duration))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 90, 120, 180, 240).forEach { minutes ->
                        Chip(
                            label = if (minutes < 60 || minutes % 60 != 0) {
                                stringResource(Res.string.live_tv_record_duration_minutes, minutes)
                            } else {
                                stringResource(Res.string.live_tv_record_duration_hours, minutes / 60)
                            },
                            selected = durationMinutes == minutes,
                            onClick = { durationMinutes = minutes },
                        )
                    }
                }
            } else {
                RecordingDialogLabel(stringResource(Res.string.live_tv_record_day))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..6).forEach { offset ->
                        val date = today.date.plus(DatePeriod(days = offset))
                        Chip(
                            label = when (offset) {
                                0 -> stringResource(Res.string.live_tv_record_today)
                                1 -> stringResource(Res.string.live_tv_record_tomorrow)
                                else -> "${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthNumber.toString().padStart(2, '0')}"
                            },
                            selected = dayOffset == offset,
                            onClick = { dayOffset = offset },
                        )
                    }
                }
                RecordingTimeStepper(
                    label = stringResource(Res.string.live_tv_record_start),
                    minutes = startMinutes,
                    onChange = { startMinutes = it },
                )
                RecordingTimeStepper(
                    label = stringResource(Res.string.live_tv_record_end),
                    minutes = endMinutes,
                    onChange = { endMinutes = it },
                )
                val endsNextDay = endMinutes <= startMinutes
                if (endsNextDay) {
                    Text(
                        text = stringResource(Res.string.live_tv_record_end_next_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                }
            }
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
                DialogButton(
                    text = stringResource(Res.string.live_tv_record_button),
                    style = DialogButtonStyle.Primary,
                    onClick = {
                        val scheduled = if (!scheduleMode) {
                            val start = Clock.System.now().toEpochMilliseconds()
                            LiveTvRecordingsRepository.schedule(
                                channel = channel,
                                title = channel.name,
                                startMillis = start,
                                endMillis = start + durationMinutes * MINUTE_MILLIS,
                                source = LiveTvRecordingSource.Manual,
                            )
                        } else {
                            val date: LocalDate = today.date.plus(DatePeriod(days = dayOffset))
                            val start = LocalDateTime(date, LocalTime(startMinutes / 60, startMinutes % 60)).toInstant(zone).toEpochMilliseconds()
                            var end = LocalDateTime(date, LocalTime(endMinutes / 60, endMinutes % 60)).toInstant(zone).toEpochMilliseconds()
                            if (end <= start) end += DAY_MILLIS
                            LiveTvRecordingsRepository.schedule(
                                channel = channel,
                                title = channel.name,
                                startMillis = start,
                                endMillis = end,
                                source = LiveTvRecordingSource.Manual,
                            )
                        }
                        if (scheduled != null) NuvioToastController.show(scheduledToast)
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** "Start  [−] 21:00 [+]": hours and minutes in steps (minutes by 5), wrapping around midnight. */
@Composable
private fun RecordingTimeStepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    val tokens = MaterialTheme.nuvio
    fun shift(delta: Int) = onChange(((minutes + delta) % DAY_MINUTES + DAY_MINUTES) % DAY_MINUTES)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tokens.colors.textSecondary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        StepperButton("−1h") { shift(-60) }
        StepperButton("−5") { shift(-5) }
        Text(
            text = "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.titleMedium,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        StepperButton("+5") { shift(5) }
        StepperButton("+1h") { shift(60) }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Surface(
        onClick = onClick,
        color = tokens.colors.overlaySelected,
        shape = tokens.shapes.chip,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

private const val DAY_MINUTES = 24 * 60

@Composable
private fun RecordingDialogLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.nuvio.colors.textSecondary,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Channel choice for recording a game shown on several channels. */
@Composable
internal fun LiveTvGameRecordingDialog(
    event: LiveTvSportsEvent,
    channels: List<LiveTvChannel>,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val toast = stringResource(Res.string.live_tv_record_scheduled_toast, event.name)
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.live_tv_record_game_choose_channel),
        message = event.name,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            channels.forEach { channel ->
                Surface(
                    onClick = {
                        if (LiveTvRecordingsRepository.scheduleGame(channel, event) != null) NuvioToastController.show(toast)
                        onDismiss()
                    },
                    color = tokens.colors.overlaySelected,
                    shape = tokens.shapes.chip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            }
        }
    }
}
