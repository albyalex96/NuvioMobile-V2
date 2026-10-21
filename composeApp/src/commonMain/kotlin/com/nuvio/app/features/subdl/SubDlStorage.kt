package com.nuvio.app.features.subdl

/** Per-profile storage (keys scoped with `ProfileScopedKey`) of the SubDL settings, API key included. */
internal expect object SubDlStorage {
    fun loadSettingsPayload(): String?
    fun saveSettingsPayload(payload: String)
}
