package com.nuvio.app.features.opensubtitles

/** Per-profile storage (keys scoped with `ProfileScopedKey`) of the OpenSubtitles settings, API key included. */
internal expect object OpenSubtitlesStorage {
    fun loadSettingsPayload(): String?
    fun saveSettingsPayload(payload: String)
}
