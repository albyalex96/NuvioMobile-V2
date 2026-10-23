package com.nuvio.app.features.mal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MalConnectionMode {
    DISCONNECTED,
    AWAITING_APPROVAL,
    CONNECTED,
}

enum class MalAuthError {
    MISSING_CLIENT_ID,
    INVALID_CALLBACK,
    INVALID_CALLBACK_STATE,
    AUTHORIZATION_EXPIRED,
    TOKEN_EXCHANGE_FAILED,
    INVALID_TOKEN_RESPONSE,
    AUTHORIZATION_REVOKED,
}

data class MalAuthUiState(
    val mode: MalConnectionMode = MalConnectionMode.DISCONNECTED,
    val credentialsConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val username: String? = null,
    val userId: Long? = null,
    val syncLoginAcrossDevices: Boolean = false,
    /** Revision of the credentials (login, refresh or logout time); drives the profile blob push. */
    val credentialsRevision: Long = 0L,
    val pendingAuthorizationStartedAtEpochMs: Long? = null,
    val error: MalAuthError? = null,
)

/**
 * Tokens of the MyAnimeList account. [issuedAtEpochMs] is the revision used to decide which copy
 * wins between devices: a newer login, refresh or logout replaces an older one. A logout is a
 * revision with blank tokens.
 */
@Serializable
internal data class MalCredentials(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_at") val expiresAtEpochMs: Long? = null,
    @SerialName("issued_at") val issuedAtEpochMs: Long = 0L,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("username") val username: String? = null,
) {
    val isLoggedIn: Boolean
        get() = accessToken.isNotBlank()
}

@Serializable
internal data class MalStoredAuthState(
    val syncLoginAcrossDevices: Boolean = false,
    val pendingAuthorizationState: String? = null,
    val pendingAuthorizationStartedAtEpochMs: Long? = null,
) {
    val hasPendingAuthorization: Boolean
        get() = !pendingAuthorizationState.isNullOrBlank()
}

internal sealed interface MalAuthCallback {
    data class AuthorizationCode(
        val code: String,
        val state: String,
    ) : MalAuthCallback

    data object Invalid : MalAuthCallback
    data object NotMal : MalAuthCallback
}

@Serializable
internal data class MalTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
internal data class MalUserResponse(
    val id: Long? = null,
    val name: String? = null,
)
