package com.nuvio.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.features.player.PlayerSettingsRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.compose_settings_page_anime_skip
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
        IntroDbApiKeyDialog(
            initialValue = playerSettings.introDbApiKey,
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun IntroDbApiKeyDialog(
    initialValue: String,
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
        title = stringResource(Res.string.settings_playback_introdb_api_key),
    ) {
        Text(
            text = stringResource(Res.string.settings_playback_introdb_api_key_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSecretTextField(
            value = value,
            onValueChange = {
                value = it
                errorMessage = null
            },
            label = stringResource(Res.string.settings_playback_introdb_api_key),
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
                        val isValid = com.nuvio.app.features.player.skip.SkipIntroRepository.verifyIntroDbApiKey(trimmed)
                        isVerifying = false
                        if (isValid) {
                            onSave(trimmed)
                        } else {
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
