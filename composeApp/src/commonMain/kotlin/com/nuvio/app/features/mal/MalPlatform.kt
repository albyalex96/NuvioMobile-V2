package com.nuvio.app.features.mal

/** Profile-scoped MAL storage: plain metadata plus encrypted credentials (tokens) and PKCE verifier. */
internal expect object MalAuthStorage {
    fun loadMetadataPayload(): String?
    fun saveMetadataPayload(payload: String)
    fun loadCredentialsPayload(): String?
    fun saveCredentialsPayload(value: String?)
    fun loadCodeVerifier(): String?
    fun saveCodeVerifier(value: String?)
    fun removeProfile(profileId: Int)
}
