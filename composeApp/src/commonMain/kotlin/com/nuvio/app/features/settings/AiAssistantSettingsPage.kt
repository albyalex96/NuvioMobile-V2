package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.ai.AI_ASSISTANT_LOG_TAG
import com.nuvio.app.features.ai.AiAssistantRepository
import com.nuvio.app.features.ai.AiAssistantSettings
import com.nuvio.app.features.ai.AiProvider
import com.nuvio.app.features.ai.TAVILY_KEY_URL
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private val log = Logger.withTag(AI_ASSISTANT_LOG_TAG)

internal fun LazyListScope.aiAssistantSettingsContent(
    isTablet: Boolean,
) {
    item(key = "ai_assistant_settings") {
        AiAssistantSettingsSection(isTablet = isTablet)
    }
}

private enum class AiSettingsDialog { Provider, ApiKey, Model, TavilyKey }

@Composable
private fun AiAssistantSettingsSection(
    isTablet: Boolean,
) {
    val settings by remember {
        AiAssistantRepository.ensureLoaded()
        AiAssistantRepository.uiState
    }.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<AiSettingsDialog?>(null) }
    val notSetLabel = stringResource(Res.string.settings_playback_not_set)
    val provider = settings.provider

    SettingsSection(
        title = stringResource(Res.string.ai_settings_section_general),
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.ai_settings_enable),
                description = stringResource(Res.string.ai_settings_enable_description),
                checked = settings.enabled,
                isTablet = isTablet,
                onCheckedChange = { enabled ->
                    log.i { "AI assistant ${if (enabled) "enabled" else "disabled"}" }
                    AiAssistantRepository.setEnabled(enabled)
                },
            )
        }
        AiSettingsFootnote(text = stringResource(Res.string.ai_settings_sync_notice), isTablet = isTablet)
    }

    if (settings.enabled) {
        SettingsSection(
            title = stringResource(Res.string.ai_settings_section_provider),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsNavigationRow(
                    title = stringResource(Res.string.ai_settings_provider),
                    description = provider.displayName,
                    isTablet = isTablet,
                    onClick = { dialog = AiSettingsDialog.Provider },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.ai_settings_api_key),
                    description = settings.apiKeyFor(provider).takeIf { it.isNotBlank() }?.let(::maskedSecret)
                        ?: notSetLabel,
                    isTablet = isTablet,
                    onClick = { dialog = AiSettingsDialog.ApiKey },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.ai_settings_model),
                    description = settings.modelDescription(provider),
                    isTablet = isTablet,
                    onClick = { dialog = AiSettingsDialog.Model },
                )
            }
            AiSettingsFootnote(text = stringResource(Res.string.ai_settings_fallback_help), isTablet = isTablet)
        }

        SettingsSection(
            title = stringResource(Res.string.ai_settings_section_web_search),
            isTablet = isTablet,
        ) {
            SettingsGroup(isTablet = isTablet) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.ai_settings_web_search),
                    description = stringResource(Res.string.ai_settings_web_search_description),
                    checked = settings.isWebSearchActive,
                    enabled = settings.tavilyApiKey.isNotBlank(),
                    isTablet = isTablet,
                    onCheckedChange = { enabled ->
                        log.i { "AI web search ${if (enabled) "enabled" else "disabled"}" }
                        AiAssistantRepository.setWebSearchEnabled(enabled)
                    },
                )
                SettingsGroupDivider(isTablet = isTablet)
                SettingsNavigationRow(
                    title = stringResource(Res.string.ai_settings_tavily_key),
                    description = settings.tavilyApiKey.takeIf { it.isNotBlank() }?.let(::maskedSecret)
                        ?: notSetLabel,
                    isTablet = isTablet,
                    onClick = { dialog = AiSettingsDialog.TavilyKey },
                )
            }
        }
    }

    when (dialog) {
        AiSettingsDialog.Provider -> AiProviderDialog(
            settings = settings,
            onSelect = { selected ->
                log.i { "AI provider set to ${selected.displayName}" }
                AiAssistantRepository.setProvider(selected)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        AiSettingsDialog.ApiKey -> AiSecretDialog(
            title = "${provider.displayName} - ${stringResource(Res.string.ai_settings_api_key)}",
            description = stringResource(Res.string.ai_settings_api_key_help),
            label = stringResource(Res.string.ai_settings_api_key),
            initialValue = settings.apiKeyFor(provider),
            keyUrl = provider.keyUrl,
            onSave = { value ->
                log.i { "${provider.displayName} API key ${if (value.isBlank()) "cleared" else "updated"}" }
                AiAssistantRepository.setApiKey(provider, value)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        AiSettingsDialog.Model -> AiModelDialog(
            provider = provider,
            initialValue = settings.modelFor(provider),
            onSave = { value ->
                log.i { "${provider.displayName} model set to ${value.ifBlank { provider.defaultModel }}" }
                AiAssistantRepository.setModel(provider, value)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        AiSettingsDialog.TavilyKey -> AiSecretDialog(
            title = stringResource(Res.string.ai_settings_tavily_key),
            description = stringResource(Res.string.ai_settings_tavily_description),
            label = stringResource(Res.string.ai_settings_api_key),
            initialValue = settings.tavilyApiKey,
            keyUrl = TAVILY_KEY_URL,
            onSave = { value ->
                log.i { "Tavily API key ${if (value.isBlank()) "cleared" else "updated"}" }
                AiAssistantRepository.setTavilyApiKey(value)
                // A newly saved key means the user wants the web search on.
                if (value.isNotBlank()) AiAssistantRepository.setWebSearchEnabled(true)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun AiAssistantSettings.modelDescription(provider: AiProvider): String {
    val model = modelFor(provider)
    return if (model == provider.defaultModel) {
        stringResource(Res.string.ai_settings_model_default, model)
    } else {
        model
    }
}

@Composable
private fun AiSettingsFootnote(text: String, isTablet: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.nuvio.colors.textMuted,
        modifier = Modifier.padding(
            start = if (isTablet) 20.dp else 16.dp,
            end = if (isTablet) 20.dp else 16.dp,
            top = 8.dp,
        ),
    )
}

@Composable
private fun AiProviderDialog(
    settings: AiAssistantSettings,
    onSelect: (AiProvider) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuredLabel = stringResource(Res.string.ai_settings_provider_configured)
    val notConfiguredLabel = stringResource(Res.string.ai_settings_provider_not_configured)
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.ai_settings_provider),
        message = stringResource(Res.string.ai_settings_provider_description),
    ) {
        AiProvider.entries.forEach { provider ->
            DialogOption(
                text = provider.displayName,
                description = if (settings.isConfigured(provider)) configuredLabel else notConfiguredLabel,
                selected = provider == settings.provider,
                onClick = { onSelect(provider) },
            )
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
private fun AiSecretDialog(
    title: String,
    description: String,
    label: String,
    initialValue: String,
    keyUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var value by remember { mutableStateOf(initialValue) }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = title,
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSecretTextField(
            value = value,
            onValueChange = { value = it },
            label = label,
            modifier = Modifier.fillMaxWidth(),
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.ai_settings_get_key),
                onClick = { uriHandler.openUri(keyUrl) },
            )
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
private fun AiModelDialog(
    provider: AiProvider,
    initialValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var value by remember { mutableStateOf(initialValue.takeIf { it != provider.defaultModel }.orEmpty()) }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = "${provider.displayName} - ${stringResource(Res.string.ai_settings_model)}",
    ) {
        Text(
            text = stringResource(Res.string.ai_settings_model_help, provider.defaultModel),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(provider.defaultModel) },
            label = { Text(stringResource(Res.string.ai_settings_model)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = tokens.colors.borderFocus.copy(alpha = tokens.opacity.strong),
                unfocusedBorderColor = tokens.colors.borderDefault.copy(alpha = tokens.opacity.medium),
                focusedContainerColor = tokens.colors.surface,
                unfocusedContainerColor = tokens.colors.surface,
            ),
        )
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
