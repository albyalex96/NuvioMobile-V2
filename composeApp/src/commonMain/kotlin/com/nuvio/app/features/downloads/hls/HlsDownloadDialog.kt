package com.nuvio.app.features.downloads.hls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.Menu
import com.nuvio.app.core.ui.MenuItem
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.downloads.formatDownloadBytes
import com.nuvio.app.features.player.DeviceLanguagePreferences
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.languageLabelResForCode
import com.nuvio.app.features.player.resolvePreferredAudioLanguageTargets
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag("HlsDownload")

private const val ARGUMENT_MARKER = "\u0001"

private val HLS_MENU_MAX_HEIGHT = 260.dp
private val HLS_MENU_MAX_WIDTH = 320.dp
private val HLS_SUBTITLE_MENU_MAX_WIDTH = 240.dp

/** Assumed bitrate of a separate audio rendition, only for the size estimate. */
private const val ESTIMATED_AUDIO_BITS_PER_SECOND = 160_000L

private sealed interface HlsDialogState {
    data object Loading : HlsDialogState
    data class Problem(val problem: HlsStreamProblem) : HlsDialogState
    data class Ready(val choices: HlsDownloadChoices, val durationSeconds: Double?) : HlsDialogState
}

/**
 * Reads the HLS playlist of a stream and lets the user pick the video quality, the audio tracks and
 * the subtitles to download. Only the tracks the playlist really offers for the chosen quality are
 * listed.
 */
@Composable
fun HlsDownloadDialog(
    url: String,
    headers: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (spec: HlsDownloadSpec, preferences: HlsDownloadPreferences) -> Unit,
) {
    var state by remember(url) { mutableStateOf<HlsDialogState>(HlsDialogState.Loading) }

    LaunchedEffect(url) {
        state = when (val load = loadHlsDownloadChoices(url, headers)) {
            is HlsChoicesLoad.Problem -> HlsDialogState.Problem(load.problem)
            is HlsChoicesLoad.Ready -> HlsDialogState.Ready(load.choices, load.durationSeconds)
        }
    }

    when (val current = state) {
        HlsDialogState.Loading -> DialogSurface(onDismissRequest = onDismiss) {
            HlsDialogTitle()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(Res.string.hls_download_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                )
            }
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            }
        }

        is HlsDialogState.Problem -> DialogSurface(onDismissRequest = onDismiss) {
            HlsDialogTitle()
            Text(
                text = stringResource(
                    when (current.problem) {
                        HlsStreamProblem.Live -> Res.string.hls_download_problem_live
                        HlsStreamProblem.Drm -> Res.string.hls_download_problem_drm
                        HlsStreamProblem.Unreadable -> Res.string.hls_download_problem_unreadable
                        HlsStreamProblem.Empty -> Res.string.hls_download_problem_empty
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.nuvio.colors.textSecondary,
            )
            DialogButtons {
                DialogButton(text = stringResource(Res.string.action_close), onClick = onDismiss, style = DialogButtonStyle.Primary)
            }
        }

        is HlsDialogState.Ready -> HlsChoicesDialog(current, onDismiss, onConfirm)
    }
}

internal sealed interface HlsChoicesLoad {
    data class Problem(val problem: HlsStreamProblem) : HlsChoicesLoad
    data class Ready(val choices: HlsDownloadChoices, val durationSeconds: Double?) : HlsChoicesLoad
}

/**
 * Reads the playlist of an HLS stream and builds what the download can offer. Shared by the dialog
 * and the season downloads, which apply the choices made for the first episode to the others.
 */
internal suspend fun loadHlsDownloadChoices(url: String, headers: Map<String, String>): HlsChoicesLoad {
    val analysis = HlsStreamAnalyzer.analyze(url, headers)
    val problem = analysis.problem
    if (problem != null) {
        log.w { "HLS stream can't be downloaded: $problem" }
        return HlsChoicesLoad.Problem(problem)
    }
    // Templates with a marker in place of the argument: the label rules are plain functions.
    val trackTemplate = getString(Res.string.hls_download_track, ARGUMENT_MARKER)
    val bitrateTemplate = getString(Res.string.hls_download_bitrate, ARGUMENT_MARKER)
    val strings = HlsLabelStrings(
        track = { index -> trackTemplate.replace(ARGUMENT_MARKER, index.toString()) },
        forced = getString(Res.string.hls_download_forced),
        audioDescription = getString(Res.string.hls_download_audio_description),
        hearingImpaired = getString(Res.string.hls_download_hearing_impaired),
        singleQuality = getString(Res.string.hls_download_single_quality),
        bitrate = { mbps -> bitrateTemplate.replace(ARGUMENT_MARKER, mbps) },
    )
    // Language names are resolved up front: the label rules themselves are plain functions.
    val languageNames = analysis.multivariant?.renditions.orEmpty()
        .mapNotNull { it.language }
        .distinct()
        .associateWith { code -> languageLabelResForCode(code)?.let { getString(it) } }
    // The audio selected by default follows the preferred audio language of the app settings.
    PlayerSettingsRepository.ensureLoaded()
    val playerSettings = PlayerSettingsRepository.uiState.value
    val preferredAudio = resolvePreferredAudioLanguageTargets(
        preferredAudioLanguage = playerSettings.preferredAudioLanguage,
        secondaryPreferredAudioLanguage = playerSettings.secondaryPreferredAudioLanguage,
        deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
    )
    log.d { "Preferred audio languages for the HLS download: $preferredAudio" }
    return HlsChoicesLoad.Ready(
        choices = HlsDownloadChoices(
            playlist = analysis.multivariant,
            directPlaylistUrl = analysis.playlistUrl,
            strings = strings,
            languageName = { code -> languageNames[code] },
            preferredAudioLanguages = preferredAudio,
        ),
        durationSeconds = analysis.firstMedia?.durationSeconds?.takeIf { it > 0.0 },
    )
}

@Composable
private fun HlsChoicesDialog(
    state: HlsDialogState.Ready,
    onDismiss: () -> Unit,
    onConfirm: (spec: HlsDownloadSpec, preferences: HlsDownloadPreferences) -> Unit,
) {
    val choices = state.choices
    var video by remember { mutableStateOf(choices.videoOptions.first()) }
    val audioOptions = remember(video) { choices.audioOptions(video) }
    val subtitleOptions = remember(video) { choices.subtitleOptions(video) }
    // Selections are kept by track identity (language, name, channels) rather than by playlist URL:
    // another quality can have its own audio group, and the same languages stay selected.
    var selectedAudio by remember {
        mutableStateOf(audioOptions.filter { it.id in choices.defaultAudioIds(audioOptions) }.map { it.selectionKey }.toSet())
    }
    var selectedSubtitles by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(audioOptions) {
        if (audioOptions.none { it.selectionKey in selectedAudio }) {
            val defaults = choices.defaultAudioIds(audioOptions)
            selectedAudio = audioOptions.filter { it.id in defaults }.map { it.selectionKey }.toSet()
        }
    }

    val pickedAudio = audioOptions.filter { it.selectionKey in selectedAudio }
    val pickedSubtitles = subtitleOptions.filter { it.selectionKey in selectedSubtitles }
    val audioMissing = audioOptions.isNotEmpty() && pickedAudio.isEmpty()
    val mkvCodecs = remember(video) { choices.mkvCodecs(video) }
    val tokens = MaterialTheme.nuvio

    DialogSurface(onDismissRequest = onDismiss) {
        HlsDialogTitle()
        Column(
            modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
        ) {
            HlsSelectField(
                icon = Icons.Rounded.HighQuality,
                label = stringResource(Res.string.hls_download_quality),
                value = video.label,
                enabled = choices.videoOptions.size > 1,
                closeOnSelect = true,
            ) { close ->
                choices.videoOptions.forEach { option ->
                    MenuItem(
                        text = option.label,
                        selected = option.id == video.id,
                        onClick = {
                            video = option
                            close()
                        },
                    )
                }
            }

            if (audioOptions.isEmpty()) {
                HlsInfoField(
                    icon = Icons.Rounded.Audiotrack,
                    label = stringResource(Res.string.hls_download_audio),
                    value = stringResource(Res.string.hls_download_audio_muxed_only),
                )
            } else {
                HlsSelectField(
                    icon = Icons.Rounded.Audiotrack,
                    label = stringResource(Res.string.hls_download_audio),
                    value = summary(pickedAudio.map { it.label }, stringResource(Res.string.hls_download_audio_required)),
                    enabled = true,
                    closeOnSelect = false,
                    isError = audioMissing,
                ) {
                    audioOptions.forEach { option ->
                        HlsTrackMenuItem(
                            text = option.label,
                            selected = option.selectionKey in selectedAudio,
                            onClick = { selectedAudio = selectedAudio.toggle(option.selectionKey) },
                        )
                    }
                }
            }

            if (subtitleOptions.isEmpty()) {
                HlsInfoField(
                    icon = Icons.Rounded.Subtitles,
                    label = stringResource(Res.string.hls_download_subtitles),
                    value = stringResource(Res.string.hls_download_subtitles_unavailable),
                )
            } else {
                HlsSelectField(
                    icon = Icons.Rounded.Subtitles,
                    label = stringResource(Res.string.hls_download_subtitles),
                    value = summary(pickedSubtitles.map { it.label }, stringResource(Res.string.hls_download_subtitles_none)),
                    enabled = true,
                    closeOnSelect = false,
                    menuMaxWidth = HLS_SUBTITLE_MENU_MAX_WIDTH,
                ) {
                    subtitleOptions.forEach { option ->
                        HlsTrackMenuItem(
                            text = option.label,
                            selected = option.selectionKey in selectedSubtitles,
                            onClick = { selectedSubtitles = selectedSubtitles.toggle(option.selectionKey) },
                        )
                    }
                }
            }

            val duration = state.durationSeconds
            val bandwidth = video.variant?.let { it.averageBandwidth ?: it.bandwidth }
            if (duration != null && bandwidth != null) {
                val separateAudio = pickedAudio.count { !it.isMuxed }
                val bytes = ((bandwidth + separateAudio * ESTIMATED_AUDIO_BITS_PER_SECOND) * duration / 8.0).toLong()
                HlsHint(stringResource(Res.string.hls_download_estimate, formatHlsDuration(duration), formatDownloadBytes(bytes)))
            }
            if (mkvCodecs.isNotEmpty()) {
                HlsHint(stringResource(Res.string.hls_download_mkv_hint, mkvCodecs.joinToString(", ")))
            } else if (pickedSubtitles.isNotEmpty()) {
                HlsHint(stringResource(Res.string.hls_download_mp4_subtitles_hint))
            }
        }

        DialogButtons {
            DialogButton(text = stringResource(Res.string.action_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(Res.string.hls_download_start),
                style = DialogButtonStyle.Primary,
                enabled = !audioMissing,
                onClick = {
                    val spec = choices.buildSpec(video, pickedAudio, pickedSubtitles, state.durationSeconds)
                    log.i {
                        "HLS download chosen: ${video.label}, ${spec.audio.size} audio rendition(s)" +
                            "${if (spec.includeMuxedAudio) " + muxed audio" else ""}, " +
                            "${spec.subtitles.size} subtitle(s), predicted ${spec.container}"
                    }
                    onConfirm(spec, choices.preferencesFor(video, pickedAudio, pickedSubtitles))
                },
            )
        }
    }
}

@Composable
private fun HlsSelectField(
    icon: ImageVector,
    label: String,
    value: String,
    enabled: Boolean,
    closeOnSelect: Boolean,
    isError: Boolean = false,
    menuMaxWidth: Dp = HLS_MENU_MAX_WIDTH,
    items: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        HlsFieldLabel(icon, label)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(tokens.shapes.compactCard)
                    .background(tokens.colors.textPrimary.copy(alpha = tokens.opacity.subtle))
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(horizontal = NuvioTokens.Space.s14, vertical = NuvioTokens.Space.s12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s8),
            ) {
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isError) tokens.colors.danger else tokens.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (enabled) {
                    Icon(imageVector = Icons.Rounded.ArrowDropDown, contentDescription = null, tint = tokens.colors.textMuted)
                }
            }
            // Compact list: about five options are visible, the others are reached by scrolling.
            Menu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = HLS_MENU_MAX_HEIGHT).widthIn(max = menuMaxWidth),
            ) {
                items { if (closeOnSelect) expanded = false }
            }
        }
    }
}

@Composable
private fun HlsInfoField(icon: ImageVector, label: String, value: String) {
    val tokens = MaterialTheme.nuvio
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6)) {
        HlsFieldLabel(icon, label)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted)
    }
}

@Composable
private fun HlsDialogTitle() {
    Text(
        text = stringResource(Res.string.hls_download_title),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.nuvio.colors.textPrimary,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun HlsFieldLabel(icon: ImageVector, label: String) {
    val tokens = MaterialTheme.nuvio
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s6),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tokens.colors.textSecondary, modifier = Modifier.size(18.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = tokens.colors.textSecondary)
    }
}

/**
 * Menu row of a track. A label too long for the menu is cut with an ellipsis; when the track is
 * selected it scrolls like a news ticker instead, so the whole label can be read.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HlsTrackMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = NuvioTokens.Space.s8)
            .clip(tokens.shapes.compactCard)
            .background(if (selected) tokens.colors.accent.copy(alpha = tokens.opacity.hover) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(NuvioTokens.Space.s12),
        horizontalArrangement = Arrangement.spacedBy(NuvioTokens.Space.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .then(if (selected) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = if (selected) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = tokens.colors.accent,
                modifier = Modifier.size(tokens.icons.md),
            )
        }
    }
}

@Composable
private fun HlsHint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.nuvio.colors.textMuted)
}

@Composable
private fun summary(labels: List<String>, empty: String): String = when (labels.size) {
    0 -> empty
    1, 2 -> labels.joinToString(", ")
    else -> stringResource(Res.string.hls_download_more, labels.take(2).joinToString(", "), labels.size - 2)
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

private val HlsTrackOption.selectionKey: String
    get() = listOf(
        rendition.language?.lowercase(),
        rendition.name?.lowercase(),
        rendition.channels,
        rendition.forced.toString(),
        isMuxed.toString(),
    ).joinToString("|")

private fun formatHlsDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60.0).toLong().coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "$hours h $minutes min" else "$minutes min"
}
