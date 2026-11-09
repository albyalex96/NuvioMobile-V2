package com.nuvio.app.features.livetv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.settings.ThemeSettingsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_recordings_close_close
import nuvio.composeapp.generated.resources.live_tv_recordings_close_message
import nuvio.composeapp.generated.resources.live_tv_recordings_close_minimize
import nuvio.composeapp.generated.resources.live_tv_recordings_close_title
import org.jetbrains.compose.resources.stringResource

/** Closing the Nuvio window while recording: minimise (the recording goes on) or close and stop it. */
@Composable
internal fun CloseWhileRecordingDialog(onMinimize: () -> Unit, onClose: () -> Unit, onDismiss: () -> Unit) {
    val theme by remember { ThemeSettingsRepository.selectedTheme }.collectAsStateWithLifecycle()
    NuvioTheme(appTheme = theme) {
        DialogSurface(
            onDismissRequest = onDismiss,
            title = stringResource(Res.string.live_tv_recordings_close_title),
            message = stringResource(Res.string.live_tv_recordings_close_message),
        ) {
            DialogButtons {
                DialogButton(
                    text = stringResource(Res.string.live_tv_recordings_close_close),
                    onClick = onClose,
                    style = DialogButtonStyle.Destructive,
                )
                DialogButton(
                    text = stringResource(Res.string.live_tv_recordings_close_minimize),
                    onClick = onMinimize,
                    style = DialogButtonStyle.Primary,
                )
            }
        }
    }
}
