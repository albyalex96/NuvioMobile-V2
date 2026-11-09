package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.Chip
import com.nuvio.app.features.downloads.rememberDownloadsFolderPicker
import com.nuvio.app.features.livetv.LiveTvRecordingPlatform
import com.nuvio.app.features.livetv.LiveTvRecordingsRepository
import com.nuvio.app.features.livetv.liveTvRecordingMarginChoices
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Settings → Live TV → Recordings: folder, margins and exact alarms. */
internal fun LazyListScope.liveTvRecordingsSettingsContent(isTablet: Boolean) {
    item(key = "live_tv_recordings_storage") { LiveTvRecordingsStorageSection(isTablet = isTablet) }
    item(key = "live_tv_recordings_schedule") { LiveTvRecordingsScheduleSection(isTablet = isTablet) }
}

@Composable
private fun LiveTvRecordingsStorageSection(isTablet: Boolean) {
    val options by remember {
        LiveTvRecordingsRepository.ensureLoaded()
        LiveTvRecordingsRepository.options
    }.collectAsStateWithLifecycle()
    val folderLabel by LiveTvRecordingPlatform.folderLabel.collectAsStateWithLifecycle()
    val pickFolder = rememberDownloadsFolderPicker { treeUri -> LiveTvRecordingPlatform.selectFolder(treeUri) }

    Column {
        SettingsSection(
            title = stringResource(Res.string.live_tv_recordings_settings_section_storage),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.live_tv_recordings_folder_title),
                    description = folderLabel ?: LiveTvRecordingPlatform.defaultFolderLabel ?: stringResource(Res.string.live_tv_recordings_folder_app),
                    icon = Icons.Rounded.Folder,
                    enabled = pickFolder != null,
                    isTablet = isTablet,
                    onClick = { pickFolder?.invoke() },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.live_tv_recordings_convert_title),
                    description = stringResource(Res.string.live_tv_recordings_convert_description),
                    checked = options.convertToMp4,
                    isTablet = isTablet,
                    onCheckedChange = LiveTvRecordingsRepository::setConvertToMp4,
                )
                if (folderLabel != null) {
                    SettingsGroupDivider(isTablet = isTablet)
                    SettingsNavigationRow(
                        title = stringResource(Res.string.live_tv_recordings_folder_reset_title),
                        description = stringResource(Res.string.live_tv_recordings_folder_reset_description),
                        icon = Icons.Rounded.FolderOff,
                        isTablet = isTablet,
                        onClick = LiveTvRecordingPlatform::clearFolder,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTvRecordingsScheduleSection(isTablet: Boolean) {
    val options by remember {
        LiveTvRecordingsRepository.ensureLoaded()
        LiveTvRecordingsRepository.options
    }.collectAsStateWithLifecycle()
    // The permission is granted in the system settings: checked again when the page comes back.
    var exactAlarms by remember { mutableStateOf(LiveTvRecordingPlatform.canScheduleExactAlarms()) }
    LifecycleResumeEffect(Unit) {
        exactAlarms = LiveTvRecordingPlatform.canScheduleExactAlarms()
        if (exactAlarms) LiveTvRecordingsRepository.rearmAll()
        onPauseOrDispose { }
    }
    Column {
        SettingsSection(
            title = stringResource(Res.string.live_tv_recordings_settings_section_schedule),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                // Android only: on Windows a scheduled task opens Nuvio, no permission is needed.
                if (LiveTvRecordingPlatform.usesExactAlarms) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.live_tv_recordings_exact_alarm_title),
                    description = stringResource(
                        if (exactAlarms) Res.string.live_tv_recordings_exact_alarm_on else Res.string.live_tv_recordings_exact_alarm_off,
                    ),
                    icon = Icons.Rounded.Alarm,
                    enabled = !exactAlarms,
                    isTablet = isTablet,
                    onClick = LiveTvRecordingPlatform::openExactAlarmSettings,
                )
                SettingsGroupDivider(isTablet = isTablet)
                }
                SettingsChipRow(
                    title = stringResource(Res.string.live_tv_recordings_margin_before_title),
                    description = stringResource(Res.string.live_tv_recordings_margin_before_description),
                    isTablet = isTablet,
                ) {
                    liveTvRecordingMarginChoices.forEach { minutes ->
                        Chip(
                            label = stringResource(Res.string.live_tv_recordings_margin_minutes, minutes),
                            selected = options.marginBeforeMinutes == minutes,
                            onClick = { LiveTvRecordingsRepository.setMarginBefore(minutes) },
                        )
                    }
                }
                SettingsGroupDivider(isTablet = isTablet)
                SettingsChipRow(
                    title = stringResource(Res.string.live_tv_recordings_margin_after_title),
                    description = stringResource(Res.string.live_tv_recordings_margin_after_description),
                    isTablet = isTablet,
                ) {
                    liveTvRecordingMarginChoices.forEach { minutes ->
                        Chip(
                            label = stringResource(Res.string.live_tv_recordings_margin_minutes, minutes),
                            selected = options.marginAfterMinutes == minutes,
                            onClick = { LiveTvRecordingsRepository.setMarginAfter(minutes) },
                        )
                    }
                }
            }
        }
    }
}
