package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.mediaproxy.LanMediaProxy
import com.nuvio.app.core.mediaproxy.MEDIA_PROXY_AUTO_PORT
import com.nuvio.app.core.mediaproxy.MEDIA_PROXY_CONNECT_TIMEOUT_RANGE
import com.nuvio.app.core.mediaproxy.MEDIA_PROXY_PORT_RANGE
import com.nuvio.app.core.mediaproxy.MEDIA_PROXY_READ_TIMEOUT_RANGE
import com.nuvio.app.core.mediaproxy.MediaProxySettingsRepository
import com.nuvio.app.core.mediaproxy.isValidIpv4
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.tv.TvAppRuntime
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** The Chromecast options exist only where casting does (phone app, not the TV app). */
internal val mediaProxyCastOptionsVisible: Boolean
    get() = !TvAppRuntime.isTvApp

private const val STATUS_REFRESH_MS = 3_000L

private enum class MediaProxyTextField { Port, Address, UserAgent }

internal fun LazyListScope.mediaProxySettingsContent(isTablet: Boolean) {
    item {
        SettingsSection(title = stringResource(Res.string.media_proxy_section_status), isTablet = isTablet) {
            SettingsGroup(isTablet = isTablet) {
                MediaProxyStatusGroup(isTablet = isTablet)
            }
        }
    }
    item { MediaProxyServerSection(isTablet = isTablet) }
    item { MediaProxyRequestsSection(isTablet = isTablet) }
    if (mediaProxyCastOptionsVisible) {
        item { MediaProxyCastSection(isTablet = isTablet) }
    }
    item { MediaProxyResetSection(isTablet = isTablet) }
}

@Composable
private fun MediaProxyStatusGroup(isTablet: Boolean) {
    val status by LanMediaProxy.status.collectAsStateWithLifecycle()
    val settings by MediaProxySettingsRepository.settings.collectAsStateWithLifecycle()
    // The network can change while the page is open (Wi-Fi on/off, hotspot): poll the address.
    var detectedAddress by remember { mutableStateOf(LanMediaProxy.detectedLanAddress()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(STATUS_REFRESH_MS)
            detectedAddress = LanMediaProxy.detectedLanAddress()
        }
    }

    MediaProxyStatusLine(
        label = stringResource(Res.string.media_proxy_status_server),
        value = stringResource(
            if (status.running) Res.string.media_proxy_status_running else Res.string.media_proxy_status_idle,
        ),
        isTablet = isTablet,
    )
    SettingsGroupDivider(isTablet = isTablet)
    MediaProxyStatusLine(
        label = stringResource(Res.string.media_proxy_status_address),
        value = when {
            settings.lanAddressOverride.isNotEmpty() ->
                stringResource(Res.string.media_proxy_status_manual_address, settings.lanAddressOverride)
            detectedAddress != null -> detectedAddress.orEmpty()
            else -> stringResource(Res.string.media_proxy_status_no_network)
        },
        isTablet = isTablet,
    )
    SettingsGroupDivider(isTablet = isTablet)
    MediaProxyStatusLine(
        label = stringResource(Res.string.media_proxy_status_port),
        value = status.port?.toString()
            ?: if (settings.port == MEDIA_PROXY_AUTO_PORT) {
                stringResource(Res.string.media_proxy_port_auto)
            } else {
                settings.port.toString()
            },
        isTablet = isTablet,
    )
    SettingsGroupDivider(isTablet = isTablet)
    MediaProxyStatusLine(
        label = stringResource(Res.string.media_proxy_status_sources),
        value = if (status.activeSources > 0) {
            stringResource(Res.string.media_proxy_status_sources_value, status.activeSources, status.activeLabels.joinToString(", "))
        } else {
            "0"
        },
        isTablet = isTablet,
    )
}

@Composable
private fun MediaProxyServerSection(isTablet: Boolean) {
    val settings by MediaProxySettingsRepository.settings.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<MediaProxyTextField?>(null) }
    val detectedAddress = remember(editing) { LanMediaProxy.detectedLanAddress() }

    SettingsSection(title = stringResource(Res.string.media_proxy_section_server), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.media_proxy_port_title),
                description = if (settings.port == MEDIA_PROXY_AUTO_PORT) {
                    stringResource(Res.string.media_proxy_port_auto)
                } else {
                    settings.port.toString()
                },
                isTablet = isTablet,
                onClick = { editing = MediaProxyTextField.Port },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.media_proxy_address_title),
                description = settings.lanAddressOverride.ifEmpty {
                    detectedAddress?.let { stringResource(Res.string.media_proxy_address_auto, it) }
                        ?: stringResource(Res.string.media_proxy_address_auto_none)
                },
                isTablet = isTablet,
                onClick = { editing = MediaProxyTextField.Address },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.media_proxy_keep_awake_title),
                description = stringResource(Res.string.media_proxy_keep_awake_description),
                checked = settings.keepAwake,
                isTablet = isTablet,
                onCheckedChange = { checked -> MediaProxySettingsRepository.update { it.copy(keepAwake = checked) } },
            )
        }
    }
    MediaProxyEditDialog(editing, detectedAddress, onDismiss = { editing = null })
}

@Composable
private fun MediaProxyRequestsSection(isTablet: Boolean) {
    val settings by MediaProxySettingsRepository.settings.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<MediaProxyTextField?>(null) }

    SettingsSection(title = stringResource(Res.string.media_proxy_section_requests), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.media_proxy_user_agent_title),
                description = settings.userAgent.ifEmpty { stringResource(Res.string.media_proxy_user_agent_default) },
                isTablet = isTablet,
                onClick = { editing = MediaProxyTextField.UserAgent },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSliderRow(
                title = stringResource(Res.string.media_proxy_connect_timeout_title),
                value = settings.connectTimeoutSeconds,
                valueText = { seconds -> stringResource(Res.string.media_proxy_seconds_value, seconds) },
                valueRange = MEDIA_PROXY_CONNECT_TIMEOUT_RANGE,
                step = 5,
                isTablet = isTablet,
                onValueChange = { seconds -> MediaProxySettingsRepository.update { it.copy(connectTimeoutSeconds = seconds) } },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSliderRow(
                title = stringResource(Res.string.media_proxy_read_timeout_title),
                value = settings.readTimeoutSeconds,
                valueText = { seconds -> stringResource(Res.string.media_proxy_seconds_value, seconds) },
                valueRange = MEDIA_PROXY_READ_TIMEOUT_RANGE,
                step = 5,
                isTablet = isTablet,
                onValueChange = { seconds -> MediaProxySettingsRepository.update { it.copy(readTimeoutSeconds = seconds) } },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.media_proxy_cors_title),
                description = stringResource(Res.string.media_proxy_cors_description),
                checked = settings.corsEnabled,
                isTablet = isTablet,
                onCheckedChange = { checked -> MediaProxySettingsRepository.update { it.copy(corsEnabled = checked) } },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.media_proxy_rewrite_title),
                description = stringResource(Res.string.media_proxy_rewrite_description),
                checked = settings.rewriteManifests,
                isTablet = isTablet,
                onCheckedChange = { checked -> MediaProxySettingsRepository.update { it.copy(rewriteManifests = checked) } },
            )
        }
    }
    MediaProxyEditDialog(editing, detectedAddress = null, onDismiss = { editing = null })
}

@Composable
private fun MediaProxyCastSection(isTablet: Boolean) {
    val settings by MediaProxySettingsRepository.settings.collectAsStateWithLifecycle()

    SettingsSection(title = stringResource(Res.string.media_proxy_section_cast), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = stringResource(Res.string.media_proxy_cast_always_title),
                description = stringResource(Res.string.media_proxy_cast_always_description),
                checked = settings.castAlwaysProxy,
                isTablet = isTablet,
                onCheckedChange = { checked -> MediaProxySettingsRepository.update { it.copy(castAlwaysProxy = checked) } },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSwitchRow(
                title = stringResource(Res.string.media_proxy_cast_fallback_title),
                description = stringResource(Res.string.media_proxy_cast_fallback_description),
                checked = settings.castProxyFallback,
                isTablet = isTablet,
                onCheckedChange = { checked -> MediaProxySettingsRepository.update { it.copy(castProxyFallback = checked) } },
            )
        }
    }
}

@Composable
private fun MediaProxyResetSection(isTablet: Boolean) {
    var resetDone by rememberSaveable { mutableStateOf(false) }

    SettingsSection(title = stringResource(Res.string.media_proxy_section_reset), isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.media_proxy_reset_title),
                description = if (resetDone) {
                    stringResource(Res.string.media_proxy_reset_done)
                } else {
                    stringResource(Res.string.media_proxy_reset_description)
                },
                isTablet = isTablet,
                onClick = {
                    MediaProxySettingsRepository.resetToDefaults()
                    resetDone = true
                },
            )
        }
    }
}

@Composable
private fun MediaProxyEditDialog(editing: MediaProxyTextField?, detectedAddress: String?, onDismiss: () -> Unit) {
    val settings by MediaProxySettingsRepository.settings.collectAsStateWithLifecycle()
    when (editing) {
        MediaProxyTextField.Port -> MediaProxyTextDialog(
            title = stringResource(Res.string.media_proxy_port_title),
            hint = stringResource(Res.string.media_proxy_port_hint),
            errorText = stringResource(Res.string.media_proxy_port_invalid),
            initialValue = settings.port.takeIf { it != MEDIA_PROXY_AUTO_PORT }?.toString().orEmpty(),
            placeholder = stringResource(Res.string.media_proxy_port_auto),
            keyboardType = KeyboardType.Number,
            isValid = { it.isEmpty() || it.toIntOrNull() in MEDIA_PROXY_PORT_RANGE },
            onSave = { value ->
                MediaProxySettingsRepository.update { it.copy(port = value.toIntOrNull() ?: MEDIA_PROXY_AUTO_PORT) }
            },
            onDismiss = onDismiss,
        )
        MediaProxyTextField.Address -> MediaProxyTextDialog(
            title = stringResource(Res.string.media_proxy_address_title),
            hint = stringResource(Res.string.media_proxy_address_hint),
            errorText = stringResource(Res.string.media_proxy_address_invalid),
            initialValue = settings.lanAddressOverride,
            placeholder = detectedAddress ?: stringResource(Res.string.media_proxy_address_auto_none),
            keyboardType = KeyboardType.Decimal,
            isValid = { it.isEmpty() || isValidIpv4(it) },
            onSave = { value -> MediaProxySettingsRepository.update { it.copy(lanAddressOverride = value) } },
            onDismiss = onDismiss,
        )
        MediaProxyTextField.UserAgent -> MediaProxyTextDialog(
            title = stringResource(Res.string.media_proxy_user_agent_title),
            hint = stringResource(Res.string.media_proxy_user_agent_hint),
            errorText = null,
            initialValue = settings.userAgent,
            placeholder = stringResource(Res.string.media_proxy_user_agent_default),
            keyboardType = KeyboardType.Text,
            singleLine = false,
            isValid = { true },
            onSave = { value -> MediaProxySettingsRepository.update { it.copy(userAgent = value) } },
            onDismiss = onDismiss,
        )
        null -> Unit
    }
}

@Composable
private fun MediaProxyStatusLine(label: String, value: String, isTablet: Boolean) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isTablet) 20.dp else 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textPrimary,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun MediaProxyTextDialog(
    title: String,
    hint: String,
    errorText: String?,
    initialValue: String,
    placeholder: String,
    keyboardType: KeyboardType,
    isValid: (String) -> Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    singleLine: Boolean = true,
) {
    val tokens = MaterialTheme.nuvio
    var value by rememberSaveable { mutableStateOf(initialValue) }
    val trimmed = value.trim()
    val valid = isValid(trimmed)
    DialogSurface(onDismissRequest = onDismiss, title = title) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textMuted,
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            isError = !valid,
            placeholder = { Text(placeholder) },
            supportingText = if (!valid && errorText != null) {
                { Text(errorText) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = tokens.colors.borderFocus.copy(alpha = tokens.opacity.strong),
                unfocusedBorderColor = tokens.colors.borderDefault.copy(alpha = tokens.opacity.medium),
                focusedContainerColor = tokens.colors.surface,
                unfocusedContainerColor = tokens.colors.surface,
            ),
        )
        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.media_proxy_cancel),
                onClick = onDismiss,
            )
            DialogButton(
                text = stringResource(Res.string.media_proxy_save),
                onClick = {
                    onSave(trimmed)
                    onDismiss()
                },
                style = DialogButtonStyle.Primary,
                enabled = valid,
            )
        }
    }
}
