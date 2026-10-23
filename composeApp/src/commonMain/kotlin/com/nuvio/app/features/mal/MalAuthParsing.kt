package com.nuvio.app.features.mal

import com.nuvio.app.features.simkl.base64UrlWithoutPadding
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter

internal const val MAL_AUTHORIZE_URL = "https://myanimelist.net/v1/oauth2/authorize"
internal const val MAL_TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
internal const val MAL_API_BASE_URL = "https://api.myanimelist.net/v2"
internal const val MAL_AUTHORIZATION_TIMEOUT_MS = 10L * 60L * 1_000L

/** Access tokens are refreshed this long before they expire (MAL issues them for about 31 days). */
internal const val MAL_TOKEN_REFRESH_MARGIN_MS = 24L * 60L * 60L * 1_000L

internal data class MalPkceMaterial(
    val verifier: String,
    val state: String,
)

/**
 * MAL only supports the `plain` PKCE method, so the challenge is the verifier itself.
 * 64 random bytes give an 86-character verifier, inside the 43..128 range required by RFC 7636.
 */
internal fun createMalPkceMaterial(
    verifierEntropy: ByteArray,
    stateEntropy: ByteArray,
): MalPkceMaterial {
    require(verifierEntropy.size >= 32) { "PKCE verifier entropy must be at least 32 bytes" }
    require(stateEntropy.size >= 16) { "OAuth state entropy must be at least 16 bytes" }
    val verifier = verifierEntropy.base64UrlWithoutPadding()
    require(verifier.length in 43..128) { "PKCE verifier must be 43-128 characters" }
    return MalPkceMaterial(
        verifier = verifier,
        state = stateEntropy.base64UrlWithoutPadding(),
    )
}

internal fun buildMalAuthorizationUrl(
    clientId: String,
    redirectUri: String,
    material: MalPkceMaterial,
): String = buildString {
    append(MAL_AUTHORIZE_URL)
    append("?response_type=code")
    append("&client_id=")
    append(clientId.encodeURLParameter())
    append("&redirect_uri=")
    append(redirectUri.encodeURLParameter())
    append("&code_challenge=")
    append(material.verifier.encodeURLParameter())
    append("&code_challenge_method=plain")
    append("&state=")
    append(material.state.encodeURLParameter())
}

internal fun parseMalAuthCallback(
    callbackUrl: String,
    redirectUri: String,
): MalAuthCallback {
    if (callbackUrl != redirectUri && !callbackUrl.startsWith("$redirectUri?")) {
        return MalAuthCallback.NotMal
    }
    val parsed = runCatching { Url(callbackUrl) }.getOrNull() ?: return MalAuthCallback.Invalid
    val code = parsed.parameters["code"].orEmpty().trim()
    val state = parsed.parameters["state"].orEmpty().trim()
    if (code.isBlank() || state.isBlank()) return MalAuthCallback.Invalid
    return MalAuthCallback.AuthorizationCode(code = code, state = state)
}

internal fun isMalAuthorizationExpired(
    startedAtEpochMs: Long?,
    nowEpochMs: Long,
): Boolean = startedAtEpochMs == null ||
    nowEpochMs < startedAtEpochMs ||
    nowEpochMs - startedAtEpochMs > MAL_AUTHORIZATION_TIMEOUT_MS

internal fun malAuthorizationCodeForm(
    clientId: String,
    clientSecret: String,
    code: String,
    redirectUri: String,
    codeVerifier: String,
): String = formBody(
    "client_id" to clientId,
    "client_secret" to clientSecret.takeIf(String::isNotBlank),
    "grant_type" to "authorization_code",
    "code" to code,
    "redirect_uri" to redirectUri,
    "code_verifier" to codeVerifier,
)

internal fun malRefreshTokenForm(
    clientId: String,
    clientSecret: String,
    refreshToken: String,
): String = formBody(
    "client_id" to clientId,
    "client_secret" to clientSecret.takeIf(String::isNotBlank),
    "grant_type" to "refresh_token",
    "refresh_token" to refreshToken,
)

private fun formBody(vararg fields: Pair<String, String?>): String =
    fields
        .filter { (_, value) -> value != null }
        .joinToString("&") { (key, value) -> "${key.encodeURLParameter()}=${value.orEmpty().encodeURLParameter()}" }

internal fun MalTokenResponse.toCredentials(
    nowEpochMs: Long,
    previous: MalCredentials? = null,
): MalCredentials? {
    if (accessToken.isBlank()) return null
    return MalCredentials(
        accessToken = accessToken,
        refreshToken = refreshToken.ifBlank { previous?.refreshToken.orEmpty() },
        expiresAtEpochMs = expiresIn?.takeIf { seconds -> seconds > 0L }?.let { seconds -> nowEpochMs + seconds * 1_000L },
        issuedAtEpochMs = nowEpochMs,
        userId = previous?.userId,
        username = previous?.username,
    )
}

internal fun MalCredentials.needsRefresh(nowEpochMs: Long): Boolean {
    val expiresAt = expiresAtEpochMs ?: return false
    return nowEpochMs >= expiresAt - MAL_TOKEN_REFRESH_MARGIN_MS
}

internal fun MalCredentials.isExpired(nowEpochMs: Long): Boolean {
    val expiresAt = expiresAtEpochMs ?: return false
    return nowEpochMs >= expiresAt
}

/** A synced copy wins only when it is newer than the local one, so stale blobs never undo a refresh. */
internal fun shouldApplyRemoteMalCredentials(
    local: MalCredentials?,
    remote: MalCredentials,
): Boolean = remote.issuedAtEpochMs > (local?.issuedAtEpochMs ?: 0L)
