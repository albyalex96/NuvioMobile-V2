package com.nuvio.app.features.cast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogOption
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.nuvio
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.cast_close
import nuvio.composeapp.generated.resources.cast_connected_to
import nuvio.composeapp.generated.resources.cast_connecting
import nuvio.composeapp.generated.resources.cast_picker_empty_hint
import nuvio.composeapp.generated.resources.cast_picker_searching
import nuvio.composeapp.generated.resources.cast_picker_title
import nuvio.composeapp.generated.resources.cast_stop
import nuvio.composeapp.generated.resources.cast_unknown_device
import org.jetbrains.compose.resources.stringResource

/**
 * Device chooser (no session) or session controller (connected). Discovery runs only while
 * the dialog is shown. [onConnected] fires when a session starts while the dialog is open.
 */
@Composable
internal fun CastDevicePickerDialog(
    onDismiss: () -> Unit,
    onConnected: () -> Unit = onDismiss,
) {
    val tokens = MaterialTheme.nuvio
    val state by CastPlatform.state.collectAsStateWithLifecycle()
    val devices by CastPlatform.devices.collectAsStateWithLifecycle()
    val currentOnConnected by rememberUpdatedState(onConnected)
    val openedConnected = remember { CastPlatform.state.value is CastSessionState.Connected }

    DisposableEffect(Unit) {
        CastPlatform.startDiscovery()
        onDispose { CastPlatform.stopDiscovery() }
    }
    LaunchedEffect(state) {
        if (state is CastSessionState.Connected && !openedConnected) currentOnConnected()
    }

    val unknownDevice = stringResource(Res.string.cast_unknown_device)
    when (val session = state) {
        is CastSessionState.Connected -> DialogSurface(
            onDismissRequest = onDismiss,
            title = stringResource(Res.string.cast_picker_title),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CastConnected,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                )
                Text(
                    text = stringResource(Res.string.cast_connected_to, session.deviceName ?: unknownDevice),
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.colors.textPrimary,
                )
            }
            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.cast_close),
                    onClick = onDismiss,
                )
                DialogButton(
                    text = stringResource(Res.string.cast_stop),
                    onClick = {
                        CastPlatform.disconnect()
                        onDismiss()
                    },
                    style = DialogButtonStyle.Destructive,
                )
            }
        }

        else -> DialogSurface(
            onDismissRequest = onDismiss,
            title = stringResource(Res.string.cast_picker_title),
        ) {
            val connectingName = (session as? CastSessionState.Connecting)?.let { it.deviceName ?: unknownDevice }
            if (connectingName != null) {
                ProgressRow(text = stringResource(Res.string.cast_connecting, connectingName))
            } else if (devices.isEmpty()) {
                ProgressRow(text = stringResource(Res.string.cast_picker_searching))
                Text(
                    text = stringResource(Res.string.cast_picker_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.colors.textMuted,
                )
            }
            if (devices.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    devices.forEach { device ->
                        DialogOption(
                            text = device.name,
                            description = device.description?.takeIf { it.isNotBlank() && it != device.name },
                            selected = false,
                            enabled = connectingName == null,
                            role = Role.Button,
                            onClick = { CastPlatform.selectDevice(device.id) },
                            leading = {
                                Icon(
                                    imageVector = Icons.Rounded.Cast,
                                    contentDescription = null,
                                    tint = tokens.colors.textSecondary,
                                )
                            },
                        )
                    }
                }
            }
            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.cast_close),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ProgressRow(text: String) {
    val tokens = MaterialTheme.nuvio
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = tokens.colors.accent,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.colors.textSecondary,
        )
    }
}
