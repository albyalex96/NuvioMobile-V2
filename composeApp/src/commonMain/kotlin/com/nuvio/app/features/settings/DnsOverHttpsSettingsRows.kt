package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.network.dns.DnsOverHttpsPlatform
import com.nuvio.app.core.network.dns.DnsOverHttpsProvider
import com.nuvio.app.core.network.dns.DnsOverHttpsRepository
import com.nuvio.app.core.network.dns.DnsOverHttpsSettings
import com.nuvio.app.core.network.dns.DnsOverHttpsTestResult
import com.nuvio.app.core.network.dns.DNS_OVER_HTTPS_TEST_HOST
import com.nuvio.app.core.network.dns.DohAddressParseResult
import com.nuvio.app.core.network.dns.DohBootstrapParseResult
import com.nuvio.app.core.network.dns.parseDnsOverHttpsAddress
import com.nuvio.app.core.network.dns.parseDnsOverHttpsBootstrap
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.action_save
import nuvio.composeapp.generated.resources.settings_network_doh_custom
import nuvio.composeapp.generated.resources.settings_network_doh_custom_address_hint
import nuvio.composeapp.generated.resources.settings_network_doh_custom_address_label
import nuvio.composeapp.generated.resources.settings_network_doh_custom_bootstrap_hint
import nuvio.composeapp.generated.resources.settings_network_doh_custom_bootstrap_label
import nuvio.composeapp.generated.resources.settings_network_doh_custom_url_preview
import nuvio.composeapp.generated.resources.settings_network_doh_description
import nuvio.composeapp.generated.resources.settings_network_doh_dialog_message
import nuvio.composeapp.generated.resources.settings_network_doh_error_bootstrap
import nuvio.composeapp.generated.resources.settings_network_doh_error_empty
import nuvio.composeapp.generated.resources.settings_network_doh_error_invalid_host
import nuvio.composeapp.generated.resources.settings_network_doh_error_not_https
import nuvio.composeapp.generated.resources.settings_network_doh_fallback_description
import nuvio.composeapp.generated.resources.settings_network_doh_fallback_title
import nuvio.composeapp.generated.resources.settings_network_doh_off
import nuvio.composeapp.generated.resources.settings_network_doh_sync_description
import nuvio.composeapp.generated.resources.settings_network_doh_sync_title
import nuvio.composeapp.generated.resources.settings_network_doh_test
import nuvio.composeapp.generated.resources.settings_network_doh_test_failure
import nuvio.composeapp.generated.resources.settings_network_doh_test_success
import nuvio.composeapp.generated.resources.settings_network_doh_title
import org.jetbrains.compose.resources.stringResource

/** Visible where the app can route its lookups through DoH (Android phone and TV). */
internal val dnsOverHttpsSettingsVisible: Boolean
    get() = DnsOverHttpsPlatform.isSupported

@Composable
internal fun dnsOverHttpsProviderLabel(provider: DnsOverHttpsProvider): String = when (provider) {
    DnsOverHttpsProvider.System -> stringResource(Res.string.settings_network_doh_off)
    DnsOverHttpsProvider.Custom -> stringResource(Res.string.settings_network_doh_custom)
    else -> provider.displayName
}

@Composable
internal fun DnsOverHttpsRow(isTablet: Boolean) {
    val settingsFlow = remember {
        DnsOverHttpsRepository.ensureLoaded()
        DnsOverHttpsRepository.settings
    }
    val settings by settingsFlow.collectAsStateWithLifecycle()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val tokens = MaterialTheme.nuvio

    SettingsNavigationRow(
        title = stringResource(Res.string.settings_network_doh_title),
        description = stringResource(Res.string.settings_network_doh_description),
        isTablet = isTablet,
        trailingContent = {
            Text(
                text = dnsOverHttpsProviderLabel(settings.providerType),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Long labels are cut instead of squeezing the title and the description.
                modifier = Modifier.widthIn(max = if (isTablet) 200.dp else 120.dp),
            )
        },
        onClick = { showDialog = true },
    )

    if (showDialog) {
        DnsOverHttpsDialog(
            initial = settings,
            onSave = { next -> DnsOverHttpsRepository.update { next } },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
internal fun DnsOverHttpsSyncRow(isTablet: Boolean) {
    val syncFlow = remember {
        DnsOverHttpsRepository.ensureLoaded()
        DnsOverHttpsRepository.syncAcrossDevices
    }
    val syncEnabled by syncFlow.collectAsStateWithLifecycle()
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_network_doh_sync_title),
        description = stringResource(Res.string.settings_network_doh_sync_description),
        checked = syncEnabled,
        isTablet = isTablet,
        onCheckedChange = DnsOverHttpsRepository::setSyncAcrossDevices,
    )
}

private sealed interface DohTestState {
    data object Idle : DohTestState
    data object Running : DohTestState
    data class Done(val result: DnsOverHttpsTestResult) : DohTestState
}

@Composable
private fun DnsOverHttpsDialog(
    initial: DnsOverHttpsSettings,
    onSave: (DnsOverHttpsSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val scope = rememberCoroutineScope()
    var providerId by rememberSaveable { mutableStateOf(initial.providerType.id) }
    var customAddress by rememberSaveable { mutableStateOf(initial.customAddress) }
    var customBootstrap by rememberSaveable { mutableStateOf(initial.customBootstrap) }
    var fallbackToSystem by rememberSaveable { mutableStateOf(initial.fallbackToSystem) }
    var testState by remember { mutableStateOf<DohTestState>(DohTestState.Idle) }

    val provider = DnsOverHttpsProvider.fromId(providerId)
    val draft = DnsOverHttpsSettings(
        provider = provider.id,
        customAddress = customAddress,
        customBootstrap = customBootstrap,
        fallbackToSystem = fallbackToSystem,
    ).normalized()
    val isCustom = provider == DnsOverHttpsProvider.Custom
    val addressResult = parseDnsOverHttpsAddress(customAddress)
    val bootstrapResult = parseDnsOverHttpsBootstrap(customBootstrap)
    val addressError = when (addressResult) {
        DohAddressParseResult.Error.Empty -> stringResource(Res.string.settings_network_doh_error_empty)
        DohAddressParseResult.Error.NotHttps -> stringResource(Res.string.settings_network_doh_error_not_https)
        DohAddressParseResult.Error.InvalidHost -> stringResource(Res.string.settings_network_doh_error_invalid_host)
        is DohAddressParseResult.Valid -> null
    }
    val bootstrapError = (bootstrapResult as? DohBootstrapParseResult.Invalid)?.let { invalid ->
        stringResource(Res.string.settings_network_doh_error_bootstrap, invalid.value)
    }
    val customValid = addressError == null && bootstrapError == null
    val canSave = !isCustom || customValid
    val canTest = provider != DnsOverHttpsProvider.System && canSave && testState != DohTestState.Running

    fun clearTest() {
        testState = DohTestState.Idle
    }

    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.settings_network_doh_title),
        message = stringResource(Res.string.settings_network_doh_dialog_message),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.listGap),
        ) {
            DnsOverHttpsProvider.entries.forEach { option ->
                DialogOption(
                    text = dnsOverHttpsProviderLabel(option),
                    description = option.url?.removePrefix("https://"),
                    selected = option == provider,
                    onClick = {
                        providerId = option.id
                        clearTest()
                    },
                )
            }

            if (isCustom) {
                DohTextField(
                    value = customAddress,
                    onValueChange = {
                        customAddress = it
                        clearTest()
                    },
                    label = stringResource(Res.string.settings_network_doh_custom_address_label),
                    placeholder = "dns.adguard-dns.com",
                    errorText = addressError?.takeIf { customAddress.isNotBlank() },
                    supportingText = (addressResult as? DohAddressParseResult.Valid)?.let { valid ->
                        stringResource(Res.string.settings_network_doh_custom_url_preview, valid.url)
                    } ?: stringResource(Res.string.settings_network_doh_custom_address_hint),
                    keyboardType = KeyboardType.Uri,
                )
                if ((addressResult as? DohAddressParseResult.Valid)?.hostIsIp != true) {
                    DohTextField(
                        value = customBootstrap,
                        onValueChange = {
                            customBootstrap = it
                            clearTest()
                        },
                        label = stringResource(Res.string.settings_network_doh_custom_bootstrap_label),
                        placeholder = "94.140.14.14, 94.140.15.15",
                        errorText = bootstrapError,
                        supportingText = stringResource(Res.string.settings_network_doh_custom_bootstrap_hint),
                        keyboardType = KeyboardType.Uri,
                    )
                }
            }

            if (provider != DnsOverHttpsProvider.System) {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_network_doh_fallback_title),
                    description = stringResource(Res.string.settings_network_doh_fallback_description),
                    checked = fallbackToSystem,
                    isTablet = false,
                    horizontalPadding = 0.dp,
                    onCheckedChange = { fallbackToSystem = it },
                )
            }

            when (val state = testState) {
                is DohTestState.Done -> {
                    val result = state.result
                    Text(
                        text = when (result) {
                            is DnsOverHttpsTestResult.Success -> stringResource(
                                Res.string.settings_network_doh_test_success,
                                DNS_OVER_HTTPS_TEST_HOST,
                                result.elapsedMs.toString(),
                            )
                            is DnsOverHttpsTestResult.Failure -> stringResource(
                                Res.string.settings_network_doh_test_failure,
                                result.message,
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result is DnsOverHttpsTestResult.Success) tokens.colors.accent else tokens.colors.danger,
                    )
                }
                else -> Unit
            }
        }

        DialogButtons {
            DialogButton(
                text = stringResource(Res.string.action_cancel),
                onClick = onDismiss,
            )
            if (provider != DnsOverHttpsProvider.System) {
                DialogButton(
                    text = stringResource(Res.string.settings_network_doh_test),
                    onClick = {
                        testState = DohTestState.Running
                        scope.launch {
                            testState = DohTestState.Done(DnsOverHttpsPlatform.test(draft))
                        }
                    },
                    enabled = canTest,
                    loading = testState == DohTestState.Running,
                )
            }
            DialogButton(
                text = stringResource(Res.string.action_save),
                onClick = {
                    onSave(draft)
                    onDismiss()
                },
                style = DialogButtonStyle.Primary,
                enabled = canSave,
            )
        }
    }
}

@Composable
private fun DohTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    errorText: String?,
    supportingText: String,
    keyboardType: KeyboardType,
) {
    val tokens = MaterialTheme.nuvio
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = errorText != null,
        supportingText = { Text(errorText ?: supportingText) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = tokens.colors.borderFocus.copy(alpha = tokens.opacity.strong),
            unfocusedBorderColor = tokens.colors.borderDefault.copy(alpha = tokens.opacity.medium),
            focusedContainerColor = tokens.colors.surface,
            unfocusedContainerColor = tokens.colors.surface,
        ),
    )
}
