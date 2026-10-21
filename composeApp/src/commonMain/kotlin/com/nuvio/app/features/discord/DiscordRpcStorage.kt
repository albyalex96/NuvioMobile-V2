package com.nuvio.app.features.discord

/** Per-profile storage (keys scoped with `ProfileScopedKey`) of the Discord settings, account and token. */
internal expect object DiscordRpcStorage {
    fun loadSettingsPayload(): String?
    fun saveSettingsPayload(payload: String)
    fun loadUserPayload(): String?
    fun saveUserPayload(payload: String?)
    fun loadToken(): String?
    fun saveToken(token: String?)

    /** Fingerprint of a synced token Discord rejected, so it is not applied again from the server. */
    fun loadRejectedTokenFingerprint(): String?
    fun saveRejectedTokenFingerprint(fingerprint: String?)

    /** Fingerprint of the token last pushed to / applied from the profile sync. */
    fun loadSyncedTokenFingerprint(): String?
    fun saveSyncedTokenFingerprint(fingerprint: String?)

    /**
     * Token this device carries in the profile settings blob: null = unknown (nothing exported),
     * empty = logged out explicitly. Kept apart from [loadToken] because a token Discord rejects
     * on this device is dropped locally but must not log out the other devices of the profile.
     */
    fun loadProfileSyncToken(): String?
    fun saveProfileSyncToken(token: String?)

    /** Account sign-out: forgets the profile sync tokens of every profile. */
    fun clearProfileSyncTokens()
}
