package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.ProfileSettingsSync
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.simkl.SimklPkceCrypto
import com.nuvio.app.features.simkl.constantTimeEquals
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

object MalAuthRepository : TrackingAuthProvider {
    private val log = Logger.withTag("MalAuth")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val authorizationMutex = Mutex()
    private val refreshMutex = Mutex()

    private val _uiState = MutableStateFlow(MalAuthUiState())
    val uiState: StateFlow<MalAuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.MAL,
        displayName = "MyAnimeList",
        // WATCHED_READ only routes manual watched marks to MAL: it is never a watched-history source.
        capabilities = setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.LIBRARY_READ,
            TrackingCapability.LIBRARY_WRITE,
            TrackingCapability.WATCHED_READ,
            TrackingCapability.WATCHED_WRITE,
            TrackingCapability.SCROBBLE,
        ),
    )

    private var hasLoaded = false
    private var storedState = MalStoredAuthState()
    private var credentials: MalCredentials? = null

    init {
        TrackingProviderRegistry.register(this)
    }

    override fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    override fun onProfileChanged() {
        loadFromDisk()
    }

    override fun clearLocalState() {
        hasLoaded = false
        storedState = MalStoredAuthState()
        credentials = null
        publish()
    }

    override fun removeStoredProfile(profileId: Int) {
        MalAuthStorage.removeProfile(profileId)
    }

    fun hasRequiredCredentials(): Boolean = MalConfig.CLIENT_ID.isNotBlank()

    fun onConnectRequested(): String? {
        ensureLoaded()
        if (!hasRequiredCredentials()) {
            log.w { "MAL connect requested but MAL_CLIENT_ID is missing" }
            publish(error = MalAuthError.MISSING_CLIENT_ID)
            return null
        }
        val material = createMalPkceMaterial(
            verifierEntropy = SimklPkceCrypto.secureRandomBytes(64),
            stateEntropy = SimklPkceCrypto.secureRandomBytes(32),
        )
        MalAuthStorage.saveCodeVerifier(material.verifier)
        storedState = storedState.copy(
            pendingAuthorizationState = material.state,
            pendingAuthorizationStartedAtEpochMs = nowEpochMs(),
        )
        persistMetadata()
        publish(error = null)
        log.i { "MAL authorization started" }
        return buildMalAuthorizationUrl(MalConfig.CLIENT_ID, MalConfig.REDIRECT_URI, material)
    }

    fun pendingAuthorizationUrl(): String? {
        ensureLoaded()
        val state = storedState.pendingAuthorizationState?.takeIf(String::isNotBlank) ?: return null
        val verifier = MalAuthStorage.loadCodeVerifier()?.takeIf(String::isNotBlank)
        if (verifier == null || isMalAuthorizationExpired(storedState.pendingAuthorizationStartedAtEpochMs, nowEpochMs())) {
            clearPendingAuthorization()
            persistMetadata()
            publish(error = MalAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        return buildMalAuthorizationUrl(
            clientId = MalConfig.CLIENT_ID,
            redirectUri = MalConfig.REDIRECT_URI,
            material = MalPkceMaterial(verifier = verifier, state = state),
        )
    }

    fun onCancelAuthorization() {
        ensureLoaded()
        clearPendingAuthorization()
        persistMetadata()
        publish(error = null)
    }

    override fun handleAuthCallback(url: String): Boolean {
        ensureLoaded()
        return when (val callback = parseMalAuthCallback(url, MalConfig.REDIRECT_URI)) {
            MalAuthCallback.NotMal -> false
            MalAuthCallback.Invalid -> {
                log.w { "MAL callback without code or state" }
                clearPendingAuthorization()
                persistMetadata()
                publish(error = MalAuthError.INVALID_CALLBACK)
                true
            }
            is MalAuthCallback.AuthorizationCode -> {
                scope.launch { completeAuthorization(callback) }
                true
            }
        }
    }

    /** Explicit logout: a new revision with blank tokens, so synced devices log out as well. */
    fun onDisconnectRequested() {
        ensureLoaded()
        clearPendingAuthorization()
        persistMetadata()
        saveCredentials(MalCredentials(issuedAtEpochMs = nextRevision()))
        MalListRepository.clearLocalState()
        log.i { "MAL disconnected (syncLogin=${storedState.syncLoginAcrossDevices})" }
        publish(isLoading = false, error = null)
    }

    fun setSyncLoginAcrossDevices(enabled: Boolean) {
        ensureLoaded()
        if (storedState.syncLoginAcrossDevices == enabled) return
        storedState = storedState.copy(syncLoginAcrossDevices = enabled)
        persistMetadata()
        log.i { "MAL login sync across devices set to $enabled" }
        publish()
    }

    /** Current access token, refreshed first when it is about to expire. */
    internal suspend fun validAccessToken(): String? {
        ensureLoaded()
        val current = credentials?.takeIf(MalCredentials::isLoggedIn) ?: return null
        if (!current.needsRefresh(nowEpochMs())) return current.accessToken
        return refreshCredentials(current)?.accessToken
            ?: current.accessToken.takeUnless { current.isExpired(nowEpochMs()) }
    }

    /** Called by the API client after a 401: returns a different token to retry with, or null. */
    internal suspend fun refreshAfterRejection(rejectedToken: String): String? {
        ensureLoaded()
        val current = credentials?.takeIf(MalCredentials::isLoggedIn) ?: return null
        if (current.accessToken != rejectedToken) return current.accessToken
        return refreshCredentials(current)?.accessToken?.takeIf { token -> token != rejectedToken }
    }

    internal fun onUnauthorizedResponse() {
        log.w { "MAL authorization rejected, disconnecting this device" }
        invalidateLocally(MalAuthError.AUTHORIZATION_REVOKED)
    }

    internal fun exportedCredentials(): MalCredentials? {
        ensureLoaded()
        return credentials?.takeIf { storedState.syncLoginAcrossDevices && it.issuedAtEpochMs > 0L }
    }

    internal fun isLoginSyncEnabled(): Boolean {
        ensureLoaded()
        return storedState.syncLoginAcrossDevices
    }

    /** Applies the profile blob: the sync preference first, then credentials newer than the local ones. */
    internal fun applyRemote(syncLoginAcrossDevices: Boolean?, remote: MalCredentials?) {
        ensureLoaded()
        if (syncLoginAcrossDevices != null && syncLoginAcrossDevices != storedState.syncLoginAcrossDevices) {
            storedState = storedState.copy(syncLoginAcrossDevices = syncLoginAcrossDevices)
            persistMetadata()
        }
        if (remote != null && storedState.syncLoginAcrossDevices && shouldApplyRemoteMalCredentials(credentials, remote)) {
            log.i { "MAL credentials from another device applied (loggedIn=${remote.isLoggedIn})" }
            saveCredentials(remote)
            if (remote.isLoggedIn) {
                clearPendingAuthorization()
                persistMetadata()
                MalListRepository.refreshAsync(TrackingRefreshIntent.INVALIDATED)
            } else {
                MalListRepository.clearLocalState()
            }
        }
        publish(isLoading = false, error = if (credentials?.isLoggedIn == true) null else _uiState.value.error)
    }

    private suspend fun completeAuthorization(callback: MalAuthCallback.AuthorizationCode) =
        authorizationMutex.withLock {
            publish(isLoading = true, error = null)
            val expectedState = storedState.pendingAuthorizationState
            val verifier = MalAuthStorage.loadCodeVerifier()
            if (expectedState.isNullOrBlank() || verifier.isNullOrBlank() ||
                isMalAuthorizationExpired(storedState.pendingAuthorizationStartedAtEpochMs, nowEpochMs())
            ) {
                log.w { "MAL callback arrived after the authorization expired" }
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = MalAuthError.AUTHORIZATION_EXPIRED)
                return@withLock
            }
            if (!constantTimeEquals(callback.state, expectedState)) {
                log.w { "MAL callback state mismatch" }
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = MalAuthError.INVALID_CALLBACK_STATE)
                return@withLock
            }

            val response = try {
                malHttpEngine.execute(
                    method = "POST",
                    url = MAL_TOKEN_URL,
                    headers = mapOf(
                        "Content-Type" to "application/x-www-form-urlencoded",
                        "Accept" to "application/json",
                    ),
                    body = malAuthorizationCodeForm(
                        clientId = MalConfig.CLIENT_ID,
                        clientSecret = MalConfig.CLIENT_SECRET,
                        code = callback.code,
                        redirectUri = MalConfig.REDIRECT_URI,
                        codeVerifier = verifier,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "MAL token exchange failed" }
                clearPendingAuthorization()
                persistMetadata()
                publish(isLoading = false, error = MalAuthError.TOKEN_EXCHANGE_FAILED)
                return@withLock
            }
            clearPendingAuthorization()
            persistMetadata()
            if (response.status !in 200..299) {
                log.w { "MAL token exchange rejected: HTTP ${response.status}" }
                publish(isLoading = false, error = MalAuthError.TOKEN_EXCHANGE_FAILED)
                return@withLock
            }
            val token = runCatching { json.decodeFromString<MalTokenResponse>(response.body) }
                .getOrNull()
                ?.toCredentials(nowEpochMs = nextRevision())
            if (token == null) {
                log.w { "MAL token response could not be parsed" }
                publish(isLoading = false, error = MalAuthError.INVALID_TOKEN_RESPONSE)
                return@withLock
            }
            saveCredentials(token)
            log.i { "MAL account connected" }
            publish(isLoading = false, error = null)
            fetchAndStoreUser()
            MalListRepository.refreshAsync(TrackingRefreshIntent.INVALIDATED)
        }

    private suspend fun fetchAndStoreUser() {
        val user = try {
            val response = MalApi.client.execute(MalHttpMethod.GET, "/users/@me")
            json.decodeFromString<MalUserResponse>(response.body)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "Failed to fetch the MAL user" }
            return
        }
        val current = credentials?.takeIf(MalCredentials::isLoggedIn) ?: return
        saveCredentials(current.copy(userId = user.id, username = user.name))
        log.d { "MAL user loaded (id=${user.id})" }
        publish()
    }

    private suspend fun refreshCredentials(current: MalCredentials): MalCredentials? = refreshMutex.withLock {
        val latest = credentials?.takeIf(MalCredentials::isLoggedIn) ?: return@withLock null
        if (latest.issuedAtEpochMs != current.issuedAtEpochMs) {
            log.d { "MAL credentials changed while waiting, skipping refresh" }
            return@withLock latest
        }
        if (latest.refreshToken.isBlank()) {
            log.w { "MAL credentials have no refresh token" }
            return@withLock null
        }
        val response = try {
            malHttpEngine.execute(
                method = "POST",
                url = MAL_TOKEN_URL,
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Accept" to "application/json",
                ),
                body = malRefreshTokenForm(MalConfig.CLIENT_ID, MalConfig.CLIENT_SECRET, latest.refreshToken),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "MAL token refresh failed (network), keeping the current token" }
            return@withLock null
        }
        val refreshed = response.takeIf { it.status in 200..299 }
            ?.let { runCatching { json.decodeFromString<MalTokenResponse>(it.body) }.getOrNull() }
            ?.toCredentials(nowEpochMs = nextRevision(), previous = latest)
        if (refreshed != null) {
            saveCredentials(refreshed)
            log.i { "MAL access token refreshed" }
            publish()
            return@withLock refreshed
        }
        if (response.status !in 400..401) {
            log.w { "MAL token refresh failed: HTTP ${response.status}, keeping the current token" }
            return@withLock null
        }
        // The refresh token was rejected: another device may have refreshed it already.
        log.w { "MAL refresh token rejected: HTTP ${response.status}" }
        if (storedState.syncLoginAcrossDevices) {
            log.i { "Pulling the profile blob to look for newer MAL credentials" }
            ProfileSettingsSync.pull(ProfileRepository.activeProfileId)
            val synced = credentials?.takeIf(MalCredentials::isLoggedIn)
            if (synced != null && synced.issuedAtEpochMs > latest.issuedAtEpochMs) {
                log.i { "Using the MAL credentials refreshed by another device" }
                return@withLock synced
            }
        }
        invalidateLocally(MalAuthError.AUTHORIZATION_REVOKED)
        null
    }

    /**
     * Drops the tokens on this device only: the revision stays the same, so the other devices of
     * the profile do not treat this as a logout.
     */
    private fun invalidateLocally(error: MalAuthError) {
        val revision = credentials?.issuedAtEpochMs ?: 0L
        saveCredentials(MalCredentials(issuedAtEpochMs = revision))
        clearPendingAuthorization()
        persistMetadata()
        publish(isLoading = false, error = error)
    }

    private fun loadFromDisk() {
        hasLoaded = true
        storedState = MalAuthStorage.loadMetadataPayload()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { payload ->
                runCatching { json.decodeFromString<MalStoredAuthState>(payload) }
                    .onFailure { error -> log.w(error) { "Failed to parse MAL auth metadata" } }
                    .getOrNull()
            }
            ?: MalStoredAuthState()
        credentials = MalAuthStorage.loadCredentialsPayload()
            ?.takeIf(String::isNotBlank)
            ?.let { payload ->
                runCatching { json.decodeFromString<MalCredentials>(payload) }
                    .onFailure { error -> log.w(error) { "Failed to parse MAL credentials" } }
                    .getOrNull()
            }
        if (storedState.hasPendingAuthorization &&
            isMalAuthorizationExpired(storedState.pendingAuthorizationStartedAtEpochMs, nowEpochMs())
        ) {
            clearPendingAuthorization()
            persistMetadata()
        }
        publish(isLoading = false, error = null)
    }

    private fun saveCredentials(value: MalCredentials) {
        credentials = value
        MalAuthStorage.saveCredentialsPayload(json.encodeToString(value))
    }

    private fun clearPendingAuthorization() {
        MalAuthStorage.saveCodeVerifier(null)
        storedState = storedState.copy(
            pendingAuthorizationState = null,
            pendingAuthorizationStartedAtEpochMs = null,
        )
    }

    private fun persistMetadata() {
        MalAuthStorage.saveMetadataPayload(json.encodeToString(storedState))
    }

    /** Revisions must grow even if the clock moved back, or a synced copy could win over a newer one. */
    private fun nextRevision(): Long = maxOf(nowEpochMs(), (credentials?.issuedAtEpochMs ?: 0L) + 1L)

    private fun nowEpochMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun publish(
        isLoading: Boolean = _uiState.value.isLoading,
        error: MalAuthError? = _uiState.value.error,
    ) {
        val current = credentials
        val authenticated = current?.isLoggedIn == true
        _isAuthenticated.value = authenticated
        _uiState.value = MalAuthUiState(
            mode = when {
                authenticated -> MalConnectionMode.CONNECTED
                storedState.hasPendingAuthorization -> MalConnectionMode.AWAITING_APPROVAL
                else -> MalConnectionMode.DISCONNECTED
            },
            credentialsConfigured = hasRequiredCredentials(),
            isLoading = isLoading,
            username = current?.username?.takeIf { authenticated },
            userId = current?.userId?.takeIf { authenticated },
            syncLoginAcrossDevices = storedState.syncLoginAcrossDevices,
            credentialsRevision = current?.issuedAtEpochMs ?: 0L,
            pendingAuthorizationStartedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
            error = error,
        )
    }
}
