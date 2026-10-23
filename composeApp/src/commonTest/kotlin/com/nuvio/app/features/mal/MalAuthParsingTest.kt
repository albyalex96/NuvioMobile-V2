package com.nuvio.app.features.mal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MalAuthParsingTest {
    private val material = createMalPkceMaterial(
        verifierEntropy = ByteArray(64) { it.toByte() },
        stateEntropy = ByteArray(32) { (it * 3).toByte() },
    )

    @Test
    fun plainPkceUsesTheVerifierAsChallenge() {
        assertEquals(86, material.verifier.length)
        val url = buildMalAuthorizationUrl("client", "nuvio://auth/mal", material)
        assertTrue(url.startsWith("$MAL_AUTHORIZE_URL?response_type=code"))
        assertTrue("code_challenge=${material.verifier}" in url)
        assertTrue("code_challenge_method=plain" in url)
        assertTrue("redirect_uri=nuvio%3A%2F%2Fauth%2Fmal" in url)
        assertTrue("state=${material.state}" in url)
    }

    @Test
    fun callbackParsingRecognisesOnlyTheMalRedirect() {
        assertEquals(MalAuthCallback.NotMal, parseMalAuthCallback("nuvio://auth/simkl?code=a&state=b", "nuvio://auth/mal"))
        assertEquals(MalAuthCallback.NotMal, parseMalAuthCallback("nuvio://auth/malware?code=a&state=b", "nuvio://auth/mal"))
        assertEquals(MalAuthCallback.Invalid, parseMalAuthCallback("nuvio://auth/mal?code=a", "nuvio://auth/mal"))
        assertEquals(
            MalAuthCallback.AuthorizationCode(code = "a", state = "b"),
            parseMalAuthCallback("nuvio://auth/mal?code=a&state=b", "nuvio://auth/mal"),
        )
    }

    @Test
    fun formsOmitTheSecretWhenItIsBlank() {
        val withoutSecret = malRefreshTokenForm("id", "", "r t")
        assertEquals("client_id=id&grant_type=refresh_token&refresh_token=r%20t", withoutSecret)
        val withSecret = malAuthorizationCodeForm("id", "s", "c", "nuvio://auth/mal", "v")
        assertTrue("client_secret=s" in withSecret)
        assertTrue("grant_type=authorization_code" in withSecret)
        assertTrue("code_verifier=v" in withSecret)
    }

    @Test
    fun tokenResponseKeepsTheAccountAndThePreviousRefreshToken() {
        val previous = MalCredentials(
            accessToken = "old",
            refreshToken = "refresh",
            issuedAtEpochMs = 10L,
            userId = 7L,
            username = "user",
        )
        val refreshed = MalTokenResponse(accessToken = "new", expiresIn = 100L)
            .toCredentials(nowEpochMs = 1_000L, previous = previous)!!
        assertEquals("new", refreshed.accessToken)
        assertEquals("refresh", refreshed.refreshToken)
        assertEquals(101_000L, refreshed.expiresAtEpochMs)
        assertEquals(1_000L, refreshed.issuedAtEpochMs)
        assertEquals(7L, refreshed.userId)
        assertEquals("user", refreshed.username)
        assertNull(MalTokenResponse(accessToken = "").toCredentials(nowEpochMs = 1L))
    }

    @Test
    fun refreshHappensBeforeExpiry() {
        val credentials = MalCredentials(accessToken = "a", expiresAtEpochMs = MAL_TOKEN_REFRESH_MARGIN_MS * 2)
        assertFalse(credentials.needsRefresh(nowEpochMs = 0L))
        assertTrue(credentials.needsRefresh(nowEpochMs = MAL_TOKEN_REFRESH_MARGIN_MS))
        assertFalse(credentials.isExpired(nowEpochMs = MAL_TOKEN_REFRESH_MARGIN_MS))
        assertTrue(credentials.isExpired(nowEpochMs = MAL_TOKEN_REFRESH_MARGIN_MS * 2))
    }

    @Test
    fun onlyNewerSyncedCredentialsAreApplied() {
        val local = MalCredentials(accessToken = "a", issuedAtEpochMs = 100L)
        assertTrue(shouldApplyRemoteMalCredentials(null, MalCredentials(accessToken = "b", issuedAtEpochMs = 1L)))
        assertTrue(shouldApplyRemoteMalCredentials(local, MalCredentials(accessToken = "b", issuedAtEpochMs = 101L)))
        assertFalse(shouldApplyRemoteMalCredentials(local, MalCredentials(accessToken = "b", issuedAtEpochMs = 100L)))
        // A newer logout (blank tokens) wins as well.
        assertTrue(shouldApplyRemoteMalCredentials(local, MalCredentials(issuedAtEpochMs = 200L)))
    }

    @Test
    fun authorizationExpiresAfterTheTimeout() {
        assertTrue(isMalAuthorizationExpired(startedAtEpochMs = null, nowEpochMs = 0L))
        assertFalse(isMalAuthorizationExpired(startedAtEpochMs = 0L, nowEpochMs = MAL_AUTHORIZATION_TIMEOUT_MS))
        assertTrue(isMalAuthorizationExpired(startedAtEpochMs = 0L, nowEpochMs = MAL_AUTHORIZATION_TIMEOUT_MS + 1L))
    }
}
