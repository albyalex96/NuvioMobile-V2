package com.nuvio.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioIconActionButton
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.skip.SkipDbAdjustMode
import com.nuvio.app.features.player.skip.SkipIntroRepository
import com.nuvio.app.features.player.skip.TimestampProvider
import com.nuvio.app.features.player.skip.TimestampProviderSettings
import com.nuvio.app.features.player.skip.TimestampProviderSettingsRepository
import com.nuvio.app.features.player.skip.TimestampProviders
import com.nuvio.app.features.player.skip.selectSubmitTargets
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_done
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.addons_move_down
import nuvio.composeapp.generated.resources.addons_move_up
import nuvio.composeapp.generated.resources.aniskip_enabled
import nuvio.composeapp.generated.resources.aniskip_enabled_description
import nuvio.composeapp.generated.resources.aniskip_send_duration
import nuvio.composeapp.generated.resources.aniskip_send_duration_description
import nuvio.composeapp.generated.resources.compose_settings_page_anime_skip
import nuvio.composeapp.generated.resources.compose_settings_page_aniskip
import nuvio.composeapp.generated.resources.compose_settings_page_introdb
import nuvio.composeapp.generated.resources.settings_playback_anime_skip
import nuvio.composeapp.generated.resources.settings_playback_anime_skip_client_id
import nuvio.composeapp.generated.resources.settings_playback_anime_skip_client_id_description
import nuvio.composeapp.generated.resources.settings_playback_anime_skip_description
import nuvio.composeapp.generated.resources.settings_playback_intro_submit_enabled
import nuvio.composeapp.generated.resources.settings_playback_intro_submit_enabled_description
import nuvio.composeapp.generated.resources.settings_playback_introdb_api_key
import nuvio.composeapp.generated.resources.settings_playback_introdb_api_key_description
import nuvio.composeapp.generated.resources.settings_playback_introdb_invalid_key
import nuvio.composeapp.generated.resources.settings_playback_not_set
import nuvio.composeapp.generated.resources.skipdb_adjust_conservative
import nuvio.composeapp.generated.resources.skipdb_adjust_conservative_description
import nuvio.composeapp.generated.resources.skipdb_adjust_greedy
import nuvio.composeapp.generated.resources.skipdb_adjust_greedy_description
import nuvio.composeapp.generated.resources.skipdb_adjust_mode
import nuvio.composeapp.generated.resources.skipdb_adjust_mode_description
import nuvio.composeapp.generated.resources.skipdb_adjust_none
import nuvio.composeapp.generated.resources.skipdb_adjust_none_description
import nuvio.composeapp.generated.resources.skipdb_api_key
import nuvio.composeapp.generated.resources.skipdb_api_key_description
import nuvio.composeapp.generated.resources.skipdb_ignore_out_of_range
import nuvio.composeapp.generated.resources.skipdb_ignore_out_of_range_description
import nuvio.composeapp.generated.resources.skipdb_min_confidence
import nuvio.composeapp.generated.resources.skipdb_section_lookup
import nuvio.composeapp.generated.resources.skipdb_section_submit
import nuvio.composeapp.generated.resources.skipdb_send_duration
import nuvio.composeapp.generated.resources.skipdb_send_duration_description
import nuvio.composeapp.generated.resources.skipdb_submit_enabled
import nuvio.composeapp.generated.resources.skipdb_submit_enabled_description
import nuvio.composeapp.generated.resources.timestamp_merge_providers
import nuvio.composeapp.generated.resources.timestamp_merge_providers_description
import nuvio.composeapp.generated.resources.timestamp_priority_dialog_message
import nuvio.composeapp.generated.resources.timestamp_priority_title
import nuvio.composeapp.generated.resources.timestamp_provider_status_active
import nuvio.composeapp.generated.resources.timestamp_provider_status_inactive
import nuvio.composeapp.generated.resources.timestamp_submit_target_not_ready
import nuvio.composeapp.generated.resources.timestamp_submit_targets_dialog_message
import nuvio.composeapp.generated.resources.timestamp_submit_targets_none
import nuvio.composeapp.generated.resources.timestamp_submit_targets_title
import nuvio.composeapp.generated.resources.timestamp_submit_targets_unavailable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag("TimestampIntegrations")

internal fun LazyListScope.animeSkipSettingsContent(
    isTablet: Boolean,
) {
    item {
        AnimeSkipSettingsSection(isTablet = isTablet)
    }
}

internal fun LazyListScope.introDbSettingsContent(
    isTablet: Boolean,
) {
    item {
        IntroDbSettingsSection(isTablet = isTablet)
    }
}

internal fun LazyListScope.aniSkipSettingsContent(
    isTablet: Boolean,
) {
    item {
        AniSkipSettingsSection(isTablet = isTablet)
    }
}

internal fun LazyListScope.skipDbSettingsContent(
    isTablet: Boolean,
) {
    item {
        SkipDbLookupSection(isTablet = isTablet)
    }
    item {
        SkipDbSubmitSection(isTablet = isTablet)
    }
}

/**
 * The provider priority and submission target rows at the top of the "Segment skipping" group of
 * the Integrations page; both lists come from [TimestampProviders], so new integrations appear here
 * without changes.
 */
@Composable
internal fun ColumnScope.TimestampPreferenceRows(
    isTablet: Boolean,
) {
    TimestampProviderSettingsRepository.ensureLoaded()
    val settings by TimestampProviderSettingsRepository.uiState.collectAsStateWithLifecycle()
    // Readiness of IntroDB and AnimeSkip lives in the player settings: recompose when they change.
    val playerSettings by PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showSubmitTargetsDialog by remember { mutableStateOf(false) }

    val ordered = TimestampProviders.ordered(settings.priority)
    val submitTargets = remember(settings, playerSettings) { TimestampProviders.activeSubmitTargets(settings) }
    val anySubmitterReady = remember(settings, playerSettings) {
        TimestampProviders.submitCapable(settings.priority).any { it.submitter?.isReady() == true }
    }

    SettingsNavigationRow(
        title = stringResource(Res.string.timestamp_priority_title),
        description = ordered.joinToString(" › ") { it.displayName },
        icon = Icons.Rounded.SwapVert,
        isTablet = isTablet,
        onClick = { showPriorityDialog = true },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsNavigationRow(
        title = stringResource(Res.string.timestamp_submit_targets_title),
        description = when {
            !anySubmitterReady -> stringResource(Res.string.timestamp_submit_targets_unavailable)
            submitTargets.isEmpty() -> stringResource(Res.string.timestamp_submit_targets_none)
            else -> submitTargets.joinToString(", ") { it.displayName }
        },
        icon = Icons.Rounded.CloudUpload,
        isTablet = isTablet,
        onClick = { showSubmitTargetsDialog = true },
    )
    SettingsGroupDivider(isTablet = isTablet)
    SettingsSwitchRow(
        title = stringResource(Res.string.timestamp_merge_providers),
        description = stringResource(Res.string.timestamp_merge_providers_description),
        checked = settings.mergeProviders,
        isTablet = isTablet,
        onCheckedChange = TimestampProviderSettingsRepository::setMergeProviders,
    )

    if (showPriorityDialog) {
        TimestampPriorityDialog(
            providers = ordered,
            onSave = { ids -> TimestampProviderSettingsRepository.setPriority(ids) },
            onDismiss = { showPriorityDialog = false },
        )
    }
    if (showSubmitTargetsDialog) {
        TimestampSubmitTargetsDialog(
            settings = settings,
            onDismiss = { showSubmitTargetsDialog = false },
        )
    }
}

@Composable
private fun TimestampPriorityDialog(
    providers: List<TimestampProvider>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var ordered by remember(providers) { mutableStateOf(providers) }

    fun saveAndClose() {
        onSave(ordered.map { it.id })
        onDismiss()
    }

    fun move(from: Int, to: Int) {
        if (to !in ordered.indices) return
        ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
    }

    DialogSurface(
        onDismissRequest = ::saveAndClose,
        modifier = Modifier.heightIn(max = 620.dp),
        title = stringResource(Res.string.timestamp_priority_title),
        message = stringResource(Res.string.timestamp_priority_dialog_message),
    ) {
        val activeLabel = stringResource(Res.string.timestamp_provider_status_active)
        val inactiveLabel = stringResource(Res.string.timestamp_provider_status_inactive)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(count = ordered.size, key = { ordered[it].id }) { index ->
                val provider = ordered[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "${index + 1}. ${provider.displayName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = if (provider.isLookupActive()) activeLabel else inactiveLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    NuvioIconActionButton(
                        icon = Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(Res.string.addons_move_up),
                        tint = if (index > 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                        onClick = { move(index, index - 1) },
                    )
                    NuvioIconActionButton(
                        icon = Icons.Rounded.ArrowDownward,
                        contentDescription = stringResource(Res.string.addons_move_down),
                        tint = if (index < ordered.lastIndex) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                        onClick = { move(index, index + 1) },
                    )
                }
            }
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = ::saveAndClose,
            )
        }
    }
}

@Composable
private fun TimestampSubmitTargetsDialog(
    settings: TimestampProviderSettings,
    onDismiss: () -> Unit,
) {
    val providers = TimestampProviders.submitCapable(settings.priority)
    // Until the user chooses, the default target (highest-priority ready service) is shown selected.
    val chosen = settings.submitTargets
        ?: selectSubmitTargets(providers.filter { it.submitter?.isReady() == true }.map { it.id }, null)
    val notReadyLabel = stringResource(Res.string.timestamp_submit_target_not_ready)

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.timestamp_submit_targets_title),
        message = stringResource(Res.string.timestamp_submit_targets_dialog_message),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            providers.forEach { provider ->
                val ready = provider.submitter?.isReady() == true
                val selected = ready && provider.id in chosen
                DialogOption(
                    text = provider.displayName,
                    description = if (ready) null else notReadyLabel,
                    selected = selected,
                    enabled = ready,
                    role = Role.Checkbox,
                    onClick = {
                        val updated = if (selected) chosen - provider.id else chosen + provider.id
                        // Keep the saved list in priority order.
                        TimestampProviderSettingsRepository.setSubmitTargets(
                            providers.map { it.id }.filter { it in updated },
                        )
                    },
                )
            }
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_done),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun AnimeSkipSettingsSection(
    isTablet: Boolean,
) {
    val playerSettings by PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    var showClientIdDialog by remember { mutableStateOf(false) }
    val notSetLabel = stringResource(Res.string.settings_playback_not_set)

    SettingsSection(
        title = stringResource(Res.string.compose_settings_page_anime_skip),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_playback_anime_skip),
                description = stringResource(Res.string.settings_playback_anime_skip_description),
                checked = playerSettings.animeSkipEnabled,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    log.i { "AnimeSkip lookup ${if (enabled) "enabled" else "disabled"}" }
                    PlayerSettingsRepository.setAnimeSkipEnabled(enabled)
                },
            )
            if (playerSettings.animeSkipEnabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_playback_anime_skip_client_id),
                    description = playerSettings.animeSkipClientId.ifBlank { notSetLabel },
                    isTablet = isTablet,
                    onClick = { showClientIdDialog = true },
                )
            }
        }
    }

    if (showClientIdDialog) {
        AnimeSkipClientIdDialog(
            initialValue = playerSettings.animeSkipClientId,
            onSave = {
                log.i { "AnimeSkip client ID ${if (it.isBlank()) "cleared" else "updated"}" }
                PlayerSettingsRepository.setAnimeSkipClientId(it)
                showClientIdDialog = false
            },
            onDismiss = { showClientIdDialog = false },
        )
    }
}

@Composable
private fun IntroDbSettingsSection(
    isTablet: Boolean,
) {
    val playerSettings by PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    var showApiKeyDialog by remember { mutableStateOf(false) }
    val notSetLabel = stringResource(Res.string.settings_playback_not_set)

    SettingsSection(
        title = stringResource(Res.string.compose_settings_page_introdb),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_playback_intro_submit_enabled),
                description = stringResource(Res.string.settings_playback_intro_submit_enabled_description),
                checked = playerSettings.introSubmitEnabled,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    log.i { "IntroDB submission ${if (enabled) "enabled" else "disabled"}" }
                    PlayerSettingsRepository.setIntroSubmitEnabled(enabled)
                },
            )
            if (playerSettings.introSubmitEnabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.settings_playback_introdb_api_key),
                    description = playerSettings.introDbApiKey.ifBlank { notSetLabel },
                    isTablet = isTablet,
                    onClick = { showApiKeyDialog = true },
                )
            }
        }
    }

    if (showApiKeyDialog) {
        TimestampApiKeyDialog(
            title = stringResource(Res.string.settings_playback_introdb_api_key),
            description = stringResource(Res.string.settings_playback_introdb_api_key_description),
            initialValue = playerSettings.introDbApiKey,
            verify = SkipIntroRepository::verifyIntroDbApiKey,
            onSave = {
                log.i { "IntroDB API key ${if (it.isBlank()) "cleared" else "updated"}" }
                PlayerSettingsRepository.setIntroDbApiKey(it)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false },
        )
    }
}

@Composable
private fun AniSkipSettingsSection(
    isTablet: Boolean,
) {
    TimestampProviderSettingsRepository.ensureLoaded()
    val providerSettings by TimestampProviderSettingsRepository.uiState.collectAsStateWithLifecycle()
    val aniSkip = providerSettings.aniSkip

    SettingsSection(
        title = stringResource(Res.string.compose_settings_page_aniskip),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.aniskip_enabled),
                description = stringResource(Res.string.aniskip_enabled_description),
                checked = aniSkip.enabled,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    log.i { "AniSkip lookup ${if (enabled) "enabled" else "disabled"}" }
                    TimestampProviderSettingsRepository.updateAniSkip { it.copy(enabled = enabled) }
                },
            )
            if (aniSkip.enabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.aniskip_send_duration),
                    description = stringResource(Res.string.aniskip_send_duration_description),
                    checked = aniSkip.sendDuration,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        log.i { "AniSkip episode length ${if (enabled) "enabled" else "disabled"}" }
                        TimestampProviderSettingsRepository.updateAniSkip { it.copy(sendDuration = enabled) }
                    },
                )
            }
        }
    }
}

@Composable
private fun SkipDbLookupSection(
    isTablet: Boolean,
) {
    TimestampProviderSettingsRepository.ensureLoaded()
    val providerSettings by TimestampProviderSettingsRepository.uiState.collectAsStateWithLifecycle()
    val skipDb = providerSettings.skipDb
    var showAdjustDialog by remember { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(Res.string.skipdb_section_lookup),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.skipdb_send_duration),
                description = stringResource(Res.string.skipdb_send_duration_description),
                checked = skipDb.sendDuration,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    log.i { "SkipDB stream duration ${if (enabled) "enabled" else "disabled"}" }
                    TimestampProviderSettingsRepository.updateSkipDb { it.copy(sendDuration = enabled) }
                },
            )
            if (skipDb.sendDuration) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsSwitchRow(
                    title = stringResource(Res.string.skipdb_ignore_out_of_range),
                    description = stringResource(Res.string.skipdb_ignore_out_of_range_description),
                    checked = skipDb.ignoreOutOfRange,
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        log.i { "SkipDB out-of-range matches ${if (enabled) "ignored" else "accepted"}" }
                        TimestampProviderSettingsRepository.updateSkipDb { it.copy(ignoreOutOfRange = enabled) }
                    },
                )
            }
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSliderRow(
                title = stringResource(Res.string.skipdb_min_confidence),
                value = skipDb.minConfidencePercent,
                valueText = { "$it%" },
                valueRange = 0..100,
                step = 5,
                isTablet = isTablet,
                onValueChange = { percent ->
                    TimestampProviderSettingsRepository.updateSkipDb { it.copy(minConfidencePercent = percent) }
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.skipdb_adjust_mode),
                description = stringResource(skipDb.adjustMode.labelRes()),
                isTablet = isTablet,
                onClick = { showAdjustDialog = true },
            )
        }
    }

    if (showAdjustDialog) {
        SkipDbAdjustModeDialog(
            selected = skipDb.adjustMode,
            onSelect = { mode ->
                log.i { "SkipDB adjust mode set to ${mode.apiValue}" }
                TimestampProviderSettingsRepository.updateSkipDb { it.copy(adjustMode = mode) }
                showAdjustDialog = false
            },
            onDismiss = { showAdjustDialog = false },
        )
    }
}

@Composable
private fun SkipDbSubmitSection(
    isTablet: Boolean,
) {
    TimestampProviderSettingsRepository.ensureLoaded()
    val providerSettings by TimestampProviderSettingsRepository.uiState.collectAsStateWithLifecycle()
    val skipDb = providerSettings.skipDb
    var showApiKeyDialog by remember { mutableStateOf(false) }
    val notSetLabel = stringResource(Res.string.settings_playback_not_set)

    SettingsSection(
        title = stringResource(Res.string.skipdb_section_submit),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.skipdb_submit_enabled),
                description = stringResource(Res.string.skipdb_submit_enabled_description),
                checked = skipDb.submitEnabled,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    log.i { "SkipDB submission ${if (enabled) "enabled" else "disabled"}" }
                    TimestampProviderSettingsRepository.updateSkipDb { it.copy(submitEnabled = enabled) }
                },
            )
            if (skipDb.submitEnabled) {
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.skipdb_api_key),
                    description = skipDb.apiKey.ifBlank { notSetLabel },
                    isTablet = isTablet,
                    onClick = { showApiKeyDialog = true },
                )
            }
        }
    }

    if (showApiKeyDialog) {
        TimestampApiKeyDialog(
            title = stringResource(Res.string.skipdb_api_key),
            description = stringResource(Res.string.skipdb_api_key_description),
            initialValue = skipDb.apiKey,
            verify = SkipIntroRepository::verifySkipDbApiKey,
            onSave = { key ->
                log.i { "SkipDB API key ${if (key.isBlank()) "cleared" else "updated"}" }
                TimestampProviderSettingsRepository.updateSkipDb { it.copy(apiKey = key) }
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false },
        )
    }
}

private fun SkipDbAdjustMode.labelRes(): StringResource = when (this) {
    SkipDbAdjustMode.CONSERVATIVE -> Res.string.skipdb_adjust_conservative
    SkipDbAdjustMode.GREEDY -> Res.string.skipdb_adjust_greedy
    SkipDbAdjustMode.NONE -> Res.string.skipdb_adjust_none
}

private fun SkipDbAdjustMode.descriptionRes(): StringResource = when (this) {
    SkipDbAdjustMode.CONSERVATIVE -> Res.string.skipdb_adjust_conservative_description
    SkipDbAdjustMode.GREEDY -> Res.string.skipdb_adjust_greedy_description
    SkipDbAdjustMode.NONE -> Res.string.skipdb_adjust_none_description
}

@Composable
private fun SkipDbAdjustModeDialog(
    selected: SkipDbAdjustMode,
    onSelect: (SkipDbAdjustMode) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.skipdb_adjust_mode),
        message = stringResource(Res.string.skipdb_adjust_mode_description),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkipDbAdjustMode.entries.forEach { mode ->
                DialogOption(
                    text = stringResource(mode.labelRes()),
                    description = stringResource(mode.descriptionRes()),
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                )
            }
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AnimeSkipClientIdDialog(
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.settings_playback_anime_skip_client_id),
    ) {
        Text(
            text = stringResource(Res.string.settings_playback_anime_skip_client_id_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
            )
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(Res.string.action_save),
                onClick = { onSave(value.trim()) },
                style = DialogButtonStyle.Primary,
            )
        }
    }
}

/** API key entry for a timestamp service: a changed, non-empty key is saved only once [verify] accepts it. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TimestampApiKeyDialog(
    title: String,
    description: String,
    initialValue: String,
    verify: suspend (String) -> Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(initialValue) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val invalidKeyMessage = stringResource(Res.string.settings_playback_introdb_invalid_key)

    DialogSurface(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = title,
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSecretTextField(
            value = value,
            onValueChange = {
                value = it
                errorMessage = null
            },
            label = title,
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null,
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
                enabled = !isVerifying,
            )
            DialogButton(
                text = stringResource(Res.string.action_save),
                onClick = {
                    val trimmed = value.trim()
                    if (trimmed.isEmpty()) {
                        onSave(trimmed)
                        return@DialogButton
                    }

                    if (trimmed == initialValue) {
                        onDismiss()
                        return@DialogButton
                    }

                    isVerifying = true
                    errorMessage = null
                    scope.launch {
                        val isValid = verify(trimmed)
                        isVerifying = false
                        if (isValid) {
                            onSave(trimmed)
                        } else {
                            log.w { "$title rejected by the service" }
                            errorMessage = invalidKeyMessage
                        }
                    }
                },
                style = DialogButtonStyle.Primary,
                loading = isVerifying,
            )
        }
    }
}
