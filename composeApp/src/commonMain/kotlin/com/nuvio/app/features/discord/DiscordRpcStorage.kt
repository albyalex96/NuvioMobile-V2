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
}
