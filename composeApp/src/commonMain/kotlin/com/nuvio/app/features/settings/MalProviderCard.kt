package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.mal.MalAuthError
import com.nuvio.app.features.mal.MalAuthRepository
import com.nuvio.app.features.mal.MalConnectionMode
import com.nuvio.app.features.mal.MalListRepository
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.settings_mal_sync_now
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_mal_authorization_expired
import nuvio.composeapp.generated.resources.settings_mal_authorization_revoked
import nuvio.composeapp.generated.resources.settings_mal_connect
import nuvio.composeapp.generated.resources.settings_mal_connected_as
import nuvio.composeapp.generated.resources.settings_mal_connected_description
import nuvio.composeapp.generated.resources.settings_mal_default_user
import nuvio.composeapp.generated.resources.settings_mal_disconnect
import nuvio.composeapp.generated.resources.settings_mal_finish_sign_in
import nuvio.composeapp.generated.resources.settings_mal_invalid_callback
import nuvio.composeapp.generated.resources.settings_mal_missing_credentials
import nuvio.composeapp.generated.resources.settings_mal_open_login
import nuvio.composeapp.generated.resources.settings_mal_sign_in_description
import nuvio.composeapp.generated.resources.settings_mal_sign_in_failed
import nuvio.composeapp.generated.resources.settings_mal_visit
import nuvio.composeapp.generated.resources.settings_tracking_approval_redirect
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MalProviderCard(modifier: Modifier = Modifier) {
    val uiState by remember {
        MalAuthRepository.ensureLoaded()
        MalAuthRepository.uiState
    }.collectAsStateWithLifecycle()
    val listState by remember {
        MalListRepository.ensureLoaded()
        MalListRepository.uiState
    }.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    TrackingProviderCard(
        brand = TrackingBrand.MAL,
        mode = when (uiState.mode) {
            MalConnectionMode.DISCONNECTED -> TrackingConnectionCardMode.DISCONNECTED
            MalConnectionMode.AWAITING_APPROVAL -> TrackingConnectionCardMode.AWAITING_APPROVAL
            MalConnectionMode.CONNECTED -> TrackingConnectionCardMode.CONNECTED
        },
        credentialsConfigured = uiState.credentialsConfigured,
        isLoading = uiState.isLoading,
        connectedLabel = stringResource(
            Res.string.settings_mal_connected_as,
            uiState.username ?: stringResource(Res.string.settings_mal_default_user),
        ),
        connectedDescription = stringResource(Res.string.settings_mal_connected_description),
        signInDescription = stringResource(Res.string.settings_mal_sign_in_description),
        finishSignInLabel = stringResource(Res.string.settings_mal_finish_sign_in),
        approvalDescription = stringResource(Res.string.settings_tracking_approval_redirect),
        connectLabel = stringResource(Res.string.settings_mal_connect),
        openLoginLabel = stringResource(Res.string.settings_mal_open_login),
        disconnectLabel = stringResource(Res.string.settings_mal_disconnect),
        missingCredentialsMessage = stringResource(Res.string.settings_mal_missing_credentials),
        errorMessage = malErrorMessage(uiState.error) ?: listState.errorMessage,
        syncLabel = stringResource(Res.string.settings_mal_sync_now),
        isSyncing = listState.isLoading,
        onSyncRequested = {
            scope.launch { MalListRepository.refresh(TrackingRefreshIntent.USER_INITIATED) }
        },
        websiteLabel = stringResource(Res.string.settings_mal_visit),
        websiteUrl = MAL_WEBSITE_URL,
        onConnectRequested = MalAuthRepository::onConnectRequested,
        onResumeAuthorization = {
            MalAuthRepository.pendingAuthorizationUrl()
                ?: MalAuthRepository.onConnectRequested()
        },
        onCancelAuthorization = MalAuthRepository::onCancelAuthorization,
        onDisconnect = MalAuthRepository::onDisconnectRequested,
        modifier = modifier,
    )
}

@Composable
private fun malErrorMessage(error: MalAuthError?): String? = when (error) {
    null, MalAuthError.MISSING_CLIENT_ID -> null
    MalAuthError.INVALID_CALLBACK,
    MalAuthError.INVALID_CALLBACK_STATE,
    -> stringResource(Res.string.settings_mal_invalid_callback)
    MalAuthError.AUTHORIZATION_EXPIRED -> stringResource(Res.string.settings_mal_authorization_expired)
    MalAuthError.TOKEN_EXCHANGE_FAILED,
    MalAuthError.INVALID_TOKEN_RESPONSE,
    -> stringResource(Res.string.settings_mal_sign_in_failed)
    MalAuthError.AUTHORIZATION_REVOKED -> stringResource(Res.string.settings_mal_authorization_revoked)
}

private const val MAL_WEBSITE_URL = "https://myanimelist.net"
