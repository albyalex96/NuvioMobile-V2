package com.nuvio.app.features.home

/** Per-profile storage (keys scoped with `ProfileScopedKey`) of the "Top 10" home rows settings. */
internal expect object Top10Storage {
    fun loadSettingsPayload(): String?
    fun saveSettingsPayload(payload: String)
}
