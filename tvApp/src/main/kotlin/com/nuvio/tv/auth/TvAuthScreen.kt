package com.nuvio.tv.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import com.nuvio.app.core.auth.DeviceLinkAuthFailure
import com.nuvio.app.core.auth.DeviceLinkAuthState
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.tv.TvAuthModel
import com.nuvio.tv.R
import com.nuvio.tv.ui.TvActionButton
import com.nuvio.tv.ui.TvDimens
import com.nuvio.tv.ui.TvQrCode

private val log = Logger.withTag("TvAuth")
private val QrCardShape = RoundedCornerShape(20.dp)
private const val DEFAULT_LINK_LABEL = "nuvio.tv/link"

/** TV sign-in: scan a QR code with the phone (device link), email as a fallback. */
@Composable
fun TvAuthScreen(model: TvAuthModel) {
    val state = model.deviceLinkState
    val firstButtonRequester = remember { FocusRequester() }

    LaunchedEffect(model.deviceLinkEnabled) {
        if (model.deviceLinkEnabled && model.deviceLinkState is DeviceLinkAuthState.Idle) {
            log.i { "Starting QR sign-in" }
            model.onStartDeviceLink()
        }
    }
    LaunchedEffect(Unit) {
        repeat(10) {
            withFrameNanos { }
            if (runCatching { firstButtonRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    val background = MaterialTheme.nuvio.colors.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    0f to background,
                    0.6f to background,
                    1f to MaterialTheme.nuvio.colors.accent.copy(alpha = 0.22f),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TvDimens.screenPadding * 1.5f, vertical = TvDimens.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(
                    painter = painterResource(com.nuvio.app.R.drawable.ic_splash_logo),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
                Text(
                    text = stringResource(R.string.tv_auth_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 36.sp),
                    color = MaterialTheme.nuvio.colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.tv_auth_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.nuvio.colors.textSecondary,
                    modifier = Modifier.widthIn(max = 460.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (model.deviceLinkEnabled) {
                    val linkLabel = (state as? DeviceLinkAuthState.Waiting)?.verificationUrl?.displayLink()
                        ?: DEFAULT_LINK_LABEL
                    AuthStep(number = 1, text = stringResource(R.string.tv_auth_step_scan, linkLabel))
                    AuthStep(number = 2, text = stringResource(R.string.tv_auth_step_sign_in))
                    AuthStep(number = 3, text = stringResource(R.string.tv_auth_step_done))
                } else {
                    Text(
                        text = stringResource(R.string.tv_auth_unsupported),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.nuvio.colors.warning,
                    )
                }
                if (model.isCustomServer) {
                    Text(
                        text = stringResource(R.string.tv_auth_custom_server, model.serverUrl.removePrefix("https://")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.nuvio.colors.textMuted,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvActionButton(
                        label = stringResource(R.string.tv_auth_use_email),
                        icon = Icons.Filled.Email,
                        primary = !model.deviceLinkEnabled,
                        onClick = model.onUseEmail,
                        modifier = Modifier.focusRequester(firstButtonRequester),
                    )
                    TvActionButton(
                        label = stringResource(R.string.tv_auth_continue_without_account),
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = model.onContinueWithoutAccount,
                    )
                }
            }

            if (model.deviceLinkEnabled) {
                QrPanel(model = model, state = state)
            }
        }
    }
}

@Composable
private fun QrPanel(model: TvAuthModel, state: DeviceLinkAuthState) {
    Column(
        modifier = Modifier
            .width(340.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(QrCardShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is DeviceLinkAuthState.Waiting -> TvQrCode(
                    content = state.verificationUrl,
                    contentDescription = stringResource(R.string.tv_auth_qr_description),
                    modifier = Modifier.size(256.dp),
                )
                is DeviceLinkAuthState.Failed -> Text(
                    text = "QR",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black.copy(alpha = 0.12f),
                )
                else -> CircularProgressIndicator(color = Color.Black.copy(alpha = 0.6f))
            }
        }

        if (state is DeviceLinkAuthState.Waiting) {
            Text(
                text = stringResource(R.string.tv_auth_code_label).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.nuvio.colors.textMuted,
            )
            Text(
                text = state.code,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                ),
                color = MaterialTheme.nuvio.colors.textPrimary,
            )
        }

        val statusText = when (state) {
            DeviceLinkAuthState.Idle, DeviceLinkAuthState.Starting -> stringResource(R.string.tv_auth_preparing)
            is DeviceLinkAuthState.Waiting -> if (state.isCompleting) {
                stringResource(R.string.tv_auth_completing)
            } else {
                stringResource(R.string.tv_auth_waiting)
            }
            is DeviceLinkAuthState.Failed -> when (state.reason) {
                DeviceLinkAuthFailure.Start -> stringResource(R.string.tv_auth_failed_start)
                DeviceLinkAuthFailure.Expired -> stringResource(R.string.tv_auth_failed_expired)
                DeviceLinkAuthFailure.Complete -> model.authError ?: stringResource(R.string.tv_auth_failed_complete)
            }
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state is DeviceLinkAuthState.Failed) {
                MaterialTheme.nuvio.colors.danger
            } else {
                MaterialTheme.nuvio.colors.textSecondary
            },
            textAlign = TextAlign.Center,
        )
        if (state is DeviceLinkAuthState.Failed) {
            TvActionButton(
                label = stringResource(R.string.tv_auth_new_code),
                icon = Icons.Filled.Refresh,
                primary = true,
                onClick = model.onStartDeviceLink,
            )
        }
    }
}

@Composable
private fun AuthStep(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.nuvio.colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.nuvio.colors.onAccent,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.nuvio.colors.textPrimary,
        )
    }
}

/** "https://nuvio.tv/link?code=…" → "nuvio.tv/link" (the code is shown separately). */
private fun String.displayLink(): String =
    substringBefore('?').substringAfter("://").trimEnd('/').ifBlank { DEFAULT_LINK_LABEL }
